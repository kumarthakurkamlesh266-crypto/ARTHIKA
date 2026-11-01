package com.example.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import com.example.data.ApiKeyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class ExtractedReceipt(
    val merchant: String,
    val amount: Double,
    val dateString: String,
    val timeString: String,
    val category: String,
    val items: List<String>,
    val confidence: Float,
    val receiptImageUri: String? = null,
    val isAiPowered: Boolean = false,
    val detectedPaymentMethod: String? = null,
    val invoiceNumber: String? = null,
    val errorMessage: String? = null
)

class ReceiptScannerService(private val context: Context) {

    private val apiKeyManager by lazy { ApiKeyManager(context) }

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(35, TimeUnit.SECONDS)
            .build()
    }

    suspend fun processReceiptUri(uri: Uri): ExtractedReceipt = withContext(Dispatchers.IO) {
        val bitmap = loadAndOrientBitmap(uri)
        val apiKey = apiKeyManager.getApiKey()

        if (bitmap != null && apiKey.isNotBlank() && !apiKey.contains("MY_GEMINI_API_KEY")) {
            val geminiResult = callGeminiVision(bitmap, apiKey, uri.toString())
            if (geminiResult != null) {
                return@withContext geminiResult
            }
        }

        // Fallback when no API key configured or offline
        return@withContext fallbackLocalReceiptExtraction(uri.toString(), apiKey.isBlank())
    }

    fun getSampleReceiptPresets(): List<ExtractedReceipt> {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return listOf(
            ExtractedReceipt(
                merchant = "Trader Joe's",
                amount = 47.85,
                dateString = today,
                timeString = "14:15",
                category = "Food",
                items = listOf("Organic Sourdough Bread ($4.99)", "Greek Yogurt 32oz ($5.49)", "Fair Trade Cold Brew ($3.99)", "Wild Salmon Fillets ($18.90)", "Organic Spinach & Kale ($4.49)"),
                confidence = 0.98f,
                isAiPowered = true,
                detectedPaymentMethod = "Credit Card"
            ),
            ExtractedReceipt(
                merchant = "Apple Store Retail",
                amount = 129.00,
                dateString = today,
                timeString = "16:40",
                category = "Shopping",
                items = listOf("MagSafe Duo Wireless Charger ($129.00)"),
                confidence = 0.99f,
                isAiPowered = true,
                detectedPaymentMethod = "Apple Pay"
            ),
            ExtractedReceipt(
                merchant = "Blue Bottle Coffee",
                amount = 18.25,
                dateString = today,
                timeString = "08:50",
                category = "Food",
                items = listOf("Bella Donovan Pour Over ($6.50)", "Almond Milk Gibraltar ($5.75)", "Cardamom Morning Bun ($6.00)"),
                confidence = 0.95f,
                isAiPowered = true,
                detectedPaymentMethod = "Credit Card"
            )
        )
    }

    private fun loadAndOrientBitmap(uri: Uri): Bitmap? {
        return try {
            // First read orientation
            var orientation = ExifInterface.ORIENTATION_NORMAL
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }

            // Decode image
            val inputStream: InputStream = context.contentResolver.openInputStream(uri) ?: return null
            val fullBitmap: Bitmap? = BitmapFactory.decodeStream(inputStream)
            try { inputStream.close() } catch (e: Exception) {}
            if (fullBitmap == null) return null

            val orientedBitmap = applyExifOrientation(fullBitmap, orientation)

            // Scale to max 1536px for high OCR legibility while remaining fast
            val maxDim = 1536
            val width = orientedBitmap.width
            val height = orientedBitmap.height
            if (width <= maxDim && height <= maxDim) {
                return orientedBitmap
            }

            val ratio = width.toFloat() / height.toFloat()
            val newW: Int
            val newH: Int
            if (ratio > 1) {
                newW = maxDim
                newH = (maxDim / ratio).toInt()
            } else {
                newH = maxDim
                newW = (maxDim * ratio).toInt()
            }
            Bitmap.createScaledBitmap(orientedBitmap, newW, newH, true)
        } catch (e: Exception) {
            null
        }
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Exception) {
            bitmap
        }
    }

    private fun callGeminiVision(bitmap: Bitmap, apiKey: String, imageUri: String): ExtractedReceipt? {
        try {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            val bytes = stream.toByteArray()
            val base64Image = Base64.encodeToString(bytes, Base64.NO_WRAP)

            val prompt = """
                You are a world-class financial OCR parser for personal expense tracking.
                Analyze the provided receipt, invoice, or bill image thoroughly.
                
                Extract:
                1. Merchant / Store / Company / Vendor name.
                2. Final Total Amount (numeric number only, e.g. 45.50. Ignore currency signs $, ₹, €, £, CAD, INR, Rs, etc.).
                3. Date in YYYY-MM-DD format (if only DD/MM/YY or MM/DD/YYYY is shown, convert correctly). If no date is found, use today's date.
                4. Time in HH:mm 24-hour format (e.g. 14:30). If not visible, use current time or 12:00.
                5. Best financial Category strictly from this list: ["Food", "Transport", "Shopping", "Education", "Entertainment", "Bills", "Health", "Personal", "Other"].
                   - Supermarkets, restaurants, cafes, groceries -> "Food"
                   - Uber, taxi, gas, flights, trains, parking -> "Transport"
                   - Clothes, electronics, retail goods -> "Shopping"
                   - Electricity, internet, mobile recharge, rent, utilities -> "Bills"
                   - Pharmacy, hospital, gym, wellness -> "Health"
                   - Movies, subscriptions, games -> "Entertainment"
                6. Items: An array of item names with their individual price (e.g. ["Latte - $4.50", "Croissant - $3.75"]).
                7. Payment method: (e.g. "Credit Card", "Debit Card", "Cash", "UPI", "Apple Pay").
                8. Invoice or receipt number if visible.
                9. Confidence score between 0.0 and 1.0.

                IMPORTANT: Return ONLY valid raw JSON with NO markdown code blocks, NO ```json backticks:
                {
                  "merchant": "Vendor Name",
                  "amount": 0.00,
                  "date": "YYYY-MM-DD",
                  "time": "HH:MM",
                  "category": "Food",
                  "items": ["Item name and price"],
                  "paymentMethod": "Credit Card",
                  "invoiceNumber": "",
                  "confidence": 0.98
                }
            """.trimIndent()

            val jsonBody = JSONObject().apply {
                val contents = JSONArray().apply {
                    put(JSONObject().apply {
                        val parts = JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                            put(JSONObject().apply {
                                val inlineData = JSONObject().apply {
                                    put("mime_type", "image/jpeg")
                                    put("data", base64Image)
                                }
                                put("inline_data", inlineData)
                            })
                        }
                        put("parts", parts)
                    })
                }
                put("contents", contents)
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.1)
                    put("responseMimeType", "application/json")
                })
            }

            val model = com.example.ai.GeminiConfig.getModel(context)
            val requestUrl = com.example.ai.GeminiConfig.getGenerateContentUrl(apiKey, model)
            val request = Request.Builder()
                .url(requestUrl)
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val bodyStr = response.body?.string() ?: return null
                val rootJson = JSONObject(bodyStr)
                val candidates = rootJson.optJSONArray("candidates") ?: return null
                if (candidates.length() > 0) {
                    val candidate = candidates.getJSONObject(0)
                    val contentObj = candidate.optJSONObject("content")
                    val parts = contentObj?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        var rawText = parts.getJSONObject(0).optString("text").trim()
                        if (rawText.startsWith("```json")) {
                            rawText = rawText.removePrefix("```json").removeSuffix("```").trim()
                        } else if (rawText.startsWith("```")) {
                            rawText = rawText.removePrefix("```").removeSuffix("```").trim()
                        }
                        val extractedJson = JSONObject(rawText)
                        val itemsArray = extractedJson.optJSONArray("items")
                        val itemsList = mutableListOf<String>()
                        if (itemsArray != null) {
                            for (i in 0 until itemsArray.length()) {
                                itemsList.add(itemsArray.getString(i))
                            }
                        }

                        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                        val nowTime = SimpleDateFormat("HH:mm", Locale.US).format(Date())

                        return ExtractedReceipt(
                            merchant = extractedJson.optString("merchant", "Receipt Vendor").trim().ifBlank { "Receipt Vendor" },
                            amount = extractedJson.optDouble("amount", 0.0),
                            dateString = extractedJson.optString("date", today).trim().ifBlank { today },
                            timeString = extractedJson.optString("time", nowTime).trim().ifBlank { nowTime },
                            category = extractedJson.optString("category", "Shopping"),
                            items = itemsList,
                            confidence = extractedJson.optDouble("confidence", 0.95).toFloat(),
                            receiptImageUri = imageUri,
                            isAiPowered = true,
                            detectedPaymentMethod = extractedJson.optString("paymentMethod", "Credit Card"),
                            invoiceNumber = extractedJson.optString("invoiceNumber", "")
                        )
                    }
                }
            }
            return null
        } catch (e: Exception) {
            return null
        }
    }

    private fun fallbackLocalReceiptExtraction(imageUri: String, isApiKeyMissing: Boolean): ExtractedReceipt {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val timeNow = SimpleDateFormat("HH:mm", Locale.US).format(Date())
        return ExtractedReceipt(
            merchant = "Bill / Receipt Store",
            amount = 0.0,
            dateString = today,
            timeString = timeNow,
            category = "Food",
            items = listOf("Scanned receipt attached"),
            confidence = 0.70f,
            receiptImageUri = imageUri,
            isAiPowered = false,
            errorMessage = if (isApiKeyMissing) {
                "Add your Gemini API Key in Settings for instant AI-powered automatic OCR parsing."
            } else {
                "Unable to connect to Gemini API. Please verify your network connection or API Key in Settings."
            }
        )
    }
}
