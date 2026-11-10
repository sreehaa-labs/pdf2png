package com.example.pdf2png

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.min

@Suppress("DEPRECATION")
class MainActivity : Activity() {

    private val pickRequest = 1001
    private val accent = Color.parseColor("#1A73E8")

    private var pdfUri: Uri? = null
    private var pdfBaseName = "document"

    @Volatile
    private var converting = false

    private lateinit var pickBtn: Button
    private lateinit var convertBtn: Button
    private lateinit var fileLabel: TextView
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var quality: RadioGroup

    // ---------------------------------------------------------------- lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        updateButtons()
        val i = intent
        if (i?.action == Intent.ACTION_VIEW && i.data != null) setPdf(i.data!!)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == pickRequest && resultCode == RESULT_OK) {
            data?.data?.let { setPdf(it) }
        }
    }

    // ---------------------------------------------------------------- UI

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F7F8FA"))
            setPadding(dp(24), dp(56), dp(24), dp(24))
        }

        root.addView(label("PDF to PNG", 28f, "#111111", bold = true))
        root.addView(label("Turn every page of a PDF into a PNG image.", 14f, "#666666"), lp(4))

        pickBtn = button("Choose PDF", filled = false) { pickPdf() }
        root.addView(pickBtn, lp(32))

        fileLabel = label("No file selected", 14f, "#444444")
        root.addView(fileLabel, lp(12))

        root.addView(label("Quality", 13f, "#666666", bold = true), lp(28))
        quality = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        listOf("Standard" to 1.5f, "High" to 2.5f, "Max" to 4f).forEachIndexed { idx, (name, scale) ->
            val rb = RadioButton(this).apply {
                id = View.generateViewId()
                text = name
                tag = scale
                buttonTintList = ColorStateList.valueOf(accent)
                isChecked = (idx == 1)
            }
            quality.addView(rb, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        root.addView(quality, lp(4))

        convertBtn = button("Convert to PNG", filled = true) { convert() }
        root.addView(convertBtn, lp(28))

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            progressTintList = ColorStateList.valueOf(accent)
            visibility = View.INVISIBLE
        }
        root.addView(progress, lp(24))

        status = label("", 14f, "#444444")
        root.addView(status, lp(8))

        return root
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun lp(top: Int = 0) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(top) }

    private fun label(text: String, size: Float, color: String, bold: Boolean = false) =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(Color.parseColor(color))
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun button(text: String, filled: Boolean, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 16f
        stateListAnimator = null
        minimumHeight = dp(52)
        setTextColor(if (filled) Color.WHITE else accent)
        background = GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            if (filled) setColor(accent) else {
                setColor(Color.WHITE)
                setStroke(dp(1), accent)
            }
        }
        setOnClickListener { onClick() }
    }

    private fun updateButtons() {
        pickBtn.isEnabled = !converting
        pickBtn.alpha = if (pickBtn.isEnabled) 1f else 0.5f
        convertBtn.isEnabled = !converting && pdfUri != null
        convertBtn.alpha = if (convertBtn.isEnabled) 1f else 0.5f
    }

    private fun setStatus(msg: String, error: Boolean = false) {
        status.text = msg
        status.setTextColor(Color.parseColor(if (error) "#C62828" else "#444444"))
    }

    private fun ui(block: () -> Unit) = runOnUiThread(block)

    // ---------------------------------------------------------------- picking a PDF

    private fun pickPdf() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
        }
        startActivityForResult(i, pickRequest)
    }

    private fun setPdf(uri: Uri) {
        pdfUri = uri
        pdfBaseName = queryName(uri)
            .replace(Regex("\\.pdf$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .ifBlank { "document" }
        progress.visibility = View.INVISIBLE
        fileLabel.text = "$pdfBaseName.pdf"
        setStatus("Reading file…")
        updateButtons()

        thread {
            try {
                val renderer = openRenderer(uri)
                val pages = try { renderer.pageCount } finally { renderer.close() }
                ui { setStatus("$pages page" + if (pages == 1) "" else "s") }
            } catch (e: Throwable) {
                ui {
                    pdfUri = null
                    updateButtons()
                    setStatus("Can't open this PDF (it may be password-protected or damaged).", true)
                }
            }
        }
    }

    private fun queryName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return c.getString(idx) ?: "document"
            }
        }
        return uri.lastPathSegment ?: "document"
    }

    /** PdfRenderer needs a seekable file; fall back to a cache copy if the provider gives a stream. */
    private fun openRenderer(uri: Uri): PdfRenderer {
        try {
            val fd = contentResolver.openFileDescriptor(uri, "r") ?: throw IOException("Cannot open file")
            try {
                return PdfRenderer(fd)
            } catch (e: Exception) {
                fd.close()
                throw e
            }
        } catch (e: SecurityException) {
            throw e // password-protected PDF
        } catch (e: Exception) {
            val tmp = File(cacheDir, "input.pdf")
            contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { out -> input.copyTo(out) }
            } ?: throw e
            return PdfRenderer(ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY))
        }
    }

    // ---------------------------------------------------------------- conversion

    private fun convert() {
        val uri = pdfUri ?: return
        if (converting) return

        val checked = findViewById<RadioButton>(quality.checkedRadioButtonId)
        val scale = (checked?.tag as? Float) ?: 2.5f
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val folder = "${pdfBaseName}_$stamp"

        converting = true
        progress.progress = 0
        progress.visibility = View.VISIBLE
        setStatus("Starting…")
        updateButtons()

        thread {
            try {
                val renderer = openRenderer(uri)
                try {
                    val total = renderer.pageCount
                    val digits = max(3, total.toString().length)
                    ui { progress.max = total }

                    for (i in 0 until total) {
                        val page = renderer.openPage(i)
                        try {
                            // Cap the longest side so huge pages can't run out of memory.
                            val s = min(scale, 4096f / max(page.width, page.height))
                            val w = max(1, (page.width * s).toInt())
                            val h = max(1, (page.height * s).toInt())

                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(Color.WHITE) // PDFs are transparent by default
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                            val name = "page_" + (i + 1).toString().padStart(digits, '0') + ".png"
                            savePng(bmp, folder, name)
                            bmp.recycle()
                        } finally {
                            page.close()
                        }
                        ui {
                            progress.progress = i + 1
                            setStatus("Page ${i + 1} of $total")
                        }
                    }
                    ui {
                        converting = false
                        updateButtons()
                        setStatus("Done. $total image(s) saved to\nPictures/PDF2PNG/$folder")
                    }
                } finally {
                    renderer.close()
                }
            } catch (e: Throwable) {
                ui {
                    converting = false
                    updateButtons()
                    setStatus("Conversion failed: ${e.message ?: e.javaClass.simpleName}", true)
                }
            }
        }
    }

    /** Saves into Pictures/PDF2PNG/<folder>/ via MediaStore (no storage permission needed on Android 10+). */
    private fun savePng(bmp: Bitmap, folder: String, fileName: String) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PDF2PNG/$folder")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Could not create image file")
        try {
            contentResolver.openOutputStream(uri)?.use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: throw IOException("Could not write image file")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
        } catch (e: Exception) {
            contentResolver.delete(uri, null, null)
            throw e
        }
    }
}
