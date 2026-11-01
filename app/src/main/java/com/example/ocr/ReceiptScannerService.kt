package com.example.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.ai.GeminiConfig
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
import java.util.regex.Pattern

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

/**
 * Structured Neural Schema specification for Gemini Vision OCR extraction.
 * Guaranteed typed extraction for merchant, amount, category, date, and line items.
 */
object ReceiptNeuralSchema {
    fun buildSchema(): JSONObject {
        return JSONObject().apply {
            put("type", "OBJECT")
            put("properties", JSONObject().apply {
                put("merchant", JSONObject().apply {
                    put("type", "STRING")
                    put("description", "Name of the merchant, store, vendor, or billing entity.")
                })
                put("amount", JSONObject().apply {
                    put("type", "NUMBER")
                    put("description", "The final total bill amount as a positive numeric number (e.g. 249.50), without any currency symbols.")
                })
                put("date", JSONObject().apply {
                    put("type", "STRING")
                    put("description", "Transaction date strictly formatted as YYYY-MM-DD.")
                })
                put("time", JSONObject().apply {
                    put("type", "STRING")
                    put("description", "Transaction time in 24-hour HH:mm format (e.g. 14:30).")
                })
                put("category", JSONObject().apply {
                    put("type", "STRING")
                    put("description", "Expense category. Must be one of: Food, Transport, Shopping, Education, Entertainment, Bills, Health, Personal, Other.")
                })
                put("items", JSONObject().apply {
                    put("type", "ARRAY")
                    put("items", JSONObject().apply {
                        put("type", "STRING")
                    })
                    put("description", "List of purchased items and their prices.")
                })
                put("paymentMethod", JSONObject().apply {
                    put("type", "STRING")
                    put("description", "Payment method detected e.g. UPI, Credit Card, Debit Card, Cash, Net Banking.")
                })
                put("invoiceNumber", JSONObject().apply {
                    put("type", "STRING")
                    put("description", "Invoice, receipt, or transaction reference number.")
                })
                put("confidence", JSONObject().apply {
                    put("type", "NUMBER")
                    put("description", "Confidence score between 0.0 and 1.0.")
                })
            })
            put("required", JSONArray().apply {
                put("merchant")
                put("amount")
                put("category")
            })
        }
    }
}

class ReceiptScannerService(private val context: Context) {

    private val apiKeyManager by lazy { ApiKeyManager(context) }

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(35, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    suspend fun processReceiptUri(uri: Uri): ExtractedReceipt = withContext(Dispatchers.IO) {
        val bitmap = loadAndOrientBitmap(uri)
        if (bitmap == null) {
            return@withContext fallbackLocalReceiptExtraction(
                imageUri = uri.toString(),
                customErrorMessage = "Could not decode receipt image. Please ensure the file is an accessible JPEG, PNG, or WEBP photo."
            )
        }

        val apiKey = apiKeyManager.getApiKey()
        if (apiKey.isBlank() || apiKey.contains("MY_GEMINI_API_KEY")) {
            return@withContext fallbackLocalReceiptExtraction(
                imageUri = uri.toString(),
                customErrorMessage = "Please add your Google Gemini API Key in Settings to enable AI OCR scanning."
            )
        }

        // Attempt Gemini Vision extraction with Neural Schema & fallback hierarchy
        val extractionResult = callGeminiVisionWithFallbacks(bitmap, apiKey, uri.toString())
        return@withContext extractionResult ?: fallbackLocalReceiptExtraction(
            imageUri = uri.toString(),
            customErrorMessage = "OCR processing failed across AI models. Please verify your internet connection or Gemini API key quota."
        )
    }

    fun getSampleReceiptPresets(): List<ExtractedReceipt> {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return listOf(
            ExtractedReceipt(
                merchant = "Reliance Fresh / Supermarket",
                amount = 1485.50,
                dateString = today,
                timeString = "14:15",
                category = "Food",
                items = listOf("Aashirvaad Whole Wheat Atta 5kg (₹245.00)", "Amul Butter 500g (₹275.00)", "Tata Tea Gold 500g (₹310.00)", "Fresh Vegetables & Fruits (₹655.50)"),
                confidence = 0.98f,
                isAiPowered = true,
                detectedPaymentMethod = "UPI"
            ),
            ExtractedReceipt(
                merchant = "Croma Electronics",
                amount = 2499.00,
                dateString = today,
                timeString = "16:40",
                category = "Shopping",
                items = listOf("Noise ColorFit Smartwatch (₹2,499.00)"),
                confidence = 0.99f,
                isAiPowered = true,
                detectedPaymentMethod = "Credit Card"
            ),
            ExtractedReceipt(
                merchant = "Blue Tokai Coffee Roasters",
                amount = 485.00,
                dateString = today,
                timeString = "08:50",
                category = "Food",
                items = listOf("Iced Latte (₹240.00)", "Almond Croissant (₹245.00)"),
                confidence = 0.95f,
                isAiPowered = true,
                detectedPaymentMethod = "UPI"
            )
        )
    }

    private fun loadAndOrientBitmap(uri: Uri): Bitmap? {
        return try {
            // First read orientation
            var orientation = ExifInterface.ORIENTATION_NORMAL
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val exif = ExifInterface(stream)
                    orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                }
            } catch (e: Exception) {
                Log.w("ReceiptScanner", "Failed to read EXIF orientation: ${e.message}")
            }

            // Decode image bounds first to compute sample size (prevent OOM)
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            val maxTargetDim = 1536
            var sampleSize = 1
            var w = options.outWidth
            var h = options.outHeight

            while (w / 2 >= maxTargetDim || h / 2 >= maxTargetDim) {
                w /= 2
                h /= 2
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val fullBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return null

            val orientedBitmap = applyExifOrientation(fullBitmap, orientation)

            // Final scale if still above maxDim
            val curW = orientedBitmap.width
            val curH = orientedBitmap.height
            if (curW <= maxTargetDim && curH <= maxTargetDim) {
                return orientedBitmap
            }

            val ratio = curW.toFloat() / curH.toFloat()
            val newW: Int
            val newH: Int
            if (ratio > 1) {
                newW = maxTargetDim
                newH = (maxTargetDim / ratio).toInt()
            } else {
                newH = maxTargetDim
                newW = (maxTargetDim * ratio).toInt()
            }
            Bitmap.createScaledBitmap(orientedBitmap, newW, newH, true)
        } catch (e: Exception) {
            Log.e("ReceiptScanner", "Error decoding receipt image: ${e.message}", e)
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

    /**
     * Executes vision analysis against Gemini using the Neural Schema.
     * Implements fallback across models (gemini-3.8-flash -> gemini-2.5-flash -> gemini-1.5-flash)
     * and fallback without responseSchema if older endpoints return 400.
     */
    private fun callGeminiVisionWithFallbacks(
        bitmap: Bitmap,
        apiKey: String,
        imageUri: String
    ): ExtractedReceipt? {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 88, stream)
        val bytes = stream.toByteArray()
        val base64Image = Base64.encodeToString(bytes, Base64.NO_WRAP)

        val prompt = """
            You are ARTHIKA AI Neural OCR Engine, specialized in parsing receipts, bills, invoices, and transaction slips.
            Examine this receipt image with extreme precision.
            
            Extract the following structured fields:
            1. merchant: The name of the store, merchant, company, or vendor.
            2. amount: The final total payable amount as a positive numeric number only (e.g. 450.00). Ignore currency symbols (₹, $, Rs., etc.).
            3. date: Transaction date formatted strictly as YYYY-MM-DD. Convert DD/MM/YYYY or MM/DD/YYYY appropriately. If date is not visible, use today's date.
            4. time: Transaction time in HH:mm 24-hour format (e.g. 14:30). If not visible, use 12:00.
            5. category: Choose strictly from ["Food", "Transport", "Shopping", "Education", "Entertainment", "Bills", "Health", "Personal", "Other"].
            6. items: Array of line items with item name and price (e.g. ["Milk - ₹65.00", "Bread - ₹45.00"]).
            7. paymentMethod: Payment method detected (e.g. "UPI", "Credit Card", "Debit Card", "Cash").
            8. invoiceNumber: Invoice or receipt ID if shown.
            9. confidence: Float score between 0.0 and 1.0 indicating clarity.
            
            Return strictly valid JSON adhering to the neural schema.
        """.trimIndent()

        val primaryModel = GeminiConfig.getModel(context)
        val candidateModels = listOf(
            primaryModel,
            "gemini-3.8-flash",
            "gemini-2.5-flash",
            "gemini-1.5-flash"
        ).distinct()

        var lastErrorMessage: String? = null

        for (model in candidateModels) {
            // First try with strict Neural Schema
            val resultWithSchema = tryCallGemini(
                model = model,
                apiKey = apiKey,
                prompt = prompt,
                base64Image = base64Image,
                imageUri = imageUri,
                useResponseSchema = true
            )
            if (resultWithSchema.receipt != null) {
                return resultWithSchema.receipt
            }
            lastErrorMessage = resultWithSchema.errorMessage

            // If 400 Bad Request or Schema error, retry on same model without responseSchema
            if (resultWithSchema.shouldRetryWithoutSchema) {
                val resultPlain = tryCallGemini(
                    model = model,
                    apiKey = apiKey,
                    prompt = prompt,
                    base64Image = base64Image,
                    imageUri = imageUri,
                    useResponseSchema = false
                )
                if (resultPlain.receipt != null) {
                    return resultPlain.receipt
                }
                lastErrorMessage = resultPlain.errorMessage
            }
        }

        // If all candidate models failed, return fallback with detailed actionable error
        return fallbackLocalReceiptExtraction(imageUri, customErrorMessage = lastErrorMessage)
    }

    private data class CallAttemptResult(
        val receipt: ExtractedReceipt? = null,
        val shouldRetryWithoutSchema: Boolean = false,
        val errorMessage: String? = null
    )

    private fun tryCallGemini(
        model: String,
        apiKey: String,
        prompt: String,
        base64Image: String,
        imageUri: String,
        useResponseSchema: Boolean
    ): CallAttemptResult {
        return try {
            val jsonBody = JSONObject().apply {
                val contents = JSONArray().apply {
                    put(JSONObject().apply {
                        val parts = JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                            // Google Gemini API specification: camelCase inlineData and mimeType
                            put(JSONObject().apply {
                                val inlineData = JSONObject().apply {
                                    put("mimeType", "image/jpeg")
                                    put("data", base64Image)
                                }
                                put("inlineData", inlineData)
                            })
                        }
                        put("parts", parts)
                    })
                }
                put("contents", contents)

                val genConfig = JSONObject().apply {
                    put("temperature", 0.1)
                    put("responseMimeType", "application/json")
                    if (useResponseSchema) {
                        put("responseSchema", ReceiptNeuralSchema.buildSchema())
                    }
                }
                put("generationConfig", genConfig)
            }

            val requestUrl = GeminiConfig.getGenerateContentUrl(apiKey, model)
            val request = Request.Builder()
                .url(requestUrl)
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            val code = response.code
            val bodyStr = response.body?.string().orEmpty()

            if (response.isSuccessful) {
                val rootJson = JSONObject(bodyStr)
                val candidates = rootJson.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val candidate = candidates.getJSONObject(0)
                    val contentObj = candidate.optJSONObject("content")
                    val parts = contentObj?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        val rawText = parts.getJSONObject(0).optString("text").trim()
                        val extractedJson = parseCleanJson(rawText)
                        if (extractedJson != null) {
                            val receipt = mapJsonToReceipt(extractedJson, imageUri)
                            return CallAttemptResult(receipt = receipt)
                        }
                    }
                }
                CallAttemptResult(errorMessage = "Empty OCR response returned from Gemini ($model).")
            } else {
                val errorMsg = extractGoogleErrorMessage(bodyStr, code, model)
                val isSchemaError = code == 400 && (bodyStr.contains("response_schema", ignoreCase = true) || bodyStr.contains("responseSchema", ignoreCase = true))
                CallAttemptResult(
                    receipt = null,
                    shouldRetryWithoutSchema = isSchemaError || (code == 400 && useResponseSchema),
                    errorMessage = errorMsg
                )
            }
        } catch (e: Exception) {
            Log.e("ReceiptScanner", "Exception during Gemini OCR call ($model): ${e.message}", e)
            CallAttemptResult(errorMessage = "Network connection failed: ${e.message}")
        }
    }

    private fun parseCleanJson(text: String): JSONObject? {
        var clean = text.trim()
        if (clean.startsWith("```json")) {
            clean = clean.removePrefix("```json").removeSuffix("```").trim()
        } else if (clean.startsWith("```")) {
            clean = clean.removePrefix("```").removeSuffix("```").trim()
        }

        // Try direct parse
        try {
            return JSONObject(clean)
        } catch (e: Exception) {
            // Regex match first '{' to last '}'
            val pattern = Pattern.compile("\\{.*\\}", Pattern.DOTALL)
            val matcher = pattern.matcher(clean)
            if (matcher.find()) {
                val candidate = matcher.group()
                return try {
                    JSONObject(candidate)
                } catch (e2: Exception) {
                    null
                }
            }
        }
        return null
    }

    private fun mapJsonToReceipt(json: JSONObject, imageUri: String): ExtractedReceipt {
        val itemsArray = json.optJSONArray("items")
        val itemsList = mutableListOf<String>()
        if (itemsArray != null) {
            for (i in 0 until itemsArray.length()) {
                itemsList.add(itemsArray.optString(i))
            }
        }

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val nowTime = SimpleDateFormat("HH:mm", Locale.US).format(Date())

        val rawAmount = json.optDouble("amount", 0.0)
        val validAmount = if (rawAmount.isNaN() || rawAmount < 0) 0.0 else rawAmount

        return ExtractedReceipt(
            merchant = json.optString("merchant", "Receipt Vendor").trim().ifBlank { "Receipt Vendor" },
            amount = validAmount,
            dateString = json.optString("date", today).trim().ifBlank { today },
            timeString = json.optString("time", nowTime).trim().ifBlank { nowTime },
            category = json.optString("category", "Shopping").trim().ifBlank { "Shopping" },
            items = itemsList,
            confidence = json.optDouble("confidence", 0.96).toFloat().coerceIn(0.1f, 1.0f),
            receiptImageUri = imageUri,
            isAiPowered = true,
            detectedPaymentMethod = json.optString("paymentMethod", "UPI"),
            invoiceNumber = json.optString("invoiceNumber", "")
        )
    }

    private fun extractGoogleErrorMessage(body: String, code: Int, model: String): String {
        return try {
            val root = JSONObject(body)
            val error = root.optJSONObject("error")
            val msg = error?.optString("message", "") ?: ""
            when {
                code == 400 -> "Google API Rejected Request (HTTP 400): ${msg.take(120)}"
                code == 403 -> "API Key Permission Denied (HTTP 403). Ensure Generative Language API is active."
                code == 404 -> "Model '$model' not found (HTTP 404). Trying alternate models."
                code == 429 -> "Quota or rate limit reached (HTTP 429). Please wait a moment."
                code in 500..599 -> "Google Cloud service unavailable (HTTP $code). Retrying."
                else -> "Gemini API error (HTTP $code): ${msg.take(100)}"
            }
        } catch (e: Exception) {
            "HTTP $code error from Gemini ($model)"
        }
    }

    private fun fallbackLocalReceiptExtraction(
        imageUri: String,
        customErrorMessage: String? = null
    ): ExtractedReceipt {
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
            errorMessage = customErrorMessage ?: "Add your Gemini API Key in Settings for instant AI-powered automatic OCR parsing."
        )
    }
}
