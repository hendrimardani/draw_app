package com.example.drawapp

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.Image
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.lang.Exception

class MainActivity : AppCompatActivity() {

    private var drawingView: DrawingView? = null
    private var mImageButtonCurrentPaint: ImageButton? = null
    private var customProgressDialog: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        drawingView = findViewById(R.id.drawing_view)
        drawingView!!.setSizeForBrush(20f)

        val linearLayoutPaintColors = findViewById<LinearLayout>(R.id.ll_paint_colors)

        // It's array in ImageButton e.g [0] -> red color, [1] -> skin color
        mImageButtonCurrentPaint = linearLayoutPaintColors[3] as ImageButton
        mImageButtonCurrentPaint!!.setImageDrawable(
            // if the button was clicked
            ContextCompat.getDrawable(this, R.drawable.pallet_pressed)
        )

        val ib_brush: ImageButton = findViewById(R.id.ib_brush)
        ib_brush.setOnClickListener {
            showBrushSizeChooserDialog()
        }

        val ib_gallery: ImageButton = findViewById(R.id.ib_gallery)
        ib_gallery.setOnClickListener {
            requestStoragePermission()
        }

        val ib_save: ImageButton = findViewById(R.id.ib_save)
        ib_save.setOnClickListener {
            // Call func showProgressDialog()
            showProgressDialog()
            if (!isReadStorageAllowed()) {
                lifecycleScope.launch {
                    // We add delay() because for dismiss cancelProgressBar
                    delay(1000)
                    val flDrawView: FrameLayout = findViewById(R.id.fl_drawing_view)
                    saveBitmap(flDrawView)
                    // Call func cancelProgressDialog()
                    cancelProgressDialog()
                }
            } else {
                Toast.makeText(this@MainActivity, "Failed!!!", Toast.LENGTH_LONG).show()
            }
        }

        val ib_undo: ImageButton = findViewById(R.id.ib_undo)
        ib_undo.setOnClickListener {
            drawingView!!.onClickUndo()
        }
    }

    val openGalleryLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val imageBackground: ImageView = findViewById(R.id.iv_background)
                imageBackground.setImageURI(result.data!!.data)
            }
        }

    val requestPermission: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach {
                val perMissionName = it.key
                val isGranted = it.value
                //if permission is granted show a toast and perform operation
                if (isGranted) {
                    Toast.makeText(
                        this@MainActivity,
                        "Permission granted now you can read the storage files.",
                        Toast.LENGTH_LONG
                    ).show()
                    //perform operation
                    //Todo 1: create an intent to pick image from external storage
                    val pickIntent = Intent(
                        Intent.ACTION_PICK,
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    )
                    //Todo 6: using the intent launcher created above launch the pick intent
                    openGalleryLauncher.launch(pickIntent)
                } else {
                    //Displaying another toast if permission is not granted and this time focus on
                    //    Read external storage
                    if (perMissionName == Manifest.permission.READ_EXTERNAL_STORAGE)
                        Toast.makeText(
                            this@MainActivity,
                            "Oops you just denied the permission.",
                            Toast.LENGTH_LONG
                        ).show()
                }
            }
        }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                // Permission already granted
                val pickIntent = Intent(
                    Intent.ACTION_PICK,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                )
                openGalleryLauncher.launch(pickIntent)
            } else {
                // Permission not granted, request it
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
                Toast.makeText(
                    this,
                    "Please grant permission to access external storage.",
                    Toast.LENGTH_LONG).show()
            }
        } else {
            // Android 10 or lower, request the permission
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            ) {
                showRationalDialog(
                    "Drawing App",
                    "Drawing App needs to Access your External Storage"
                )
            } else {
                requestPermission.launch(
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                )
            }
        }
    }

    private fun showBrushSizeChooserDialog() {
        val brushDialog = Dialog(this)
        brushDialog.setContentView(R.layout.dialog_brush_size)
        brushDialog.setTitle("Brush size: ")
        val smallBtn: ImageButton = brushDialog.findViewById(R.id.ib_small_brush)
        smallBtn.setOnClickListener {
            drawingView!!.setSizeForBrush(10f)
            brushDialog.dismiss()
        }
        val mediumBtn: ImageButton = brushDialog.findViewById(R.id.ib_medium_brush)
        mediumBtn.setOnClickListener {
            drawingView!!.setSizeForBrush(20f)
            brushDialog.dismiss()
        }
        val largeBtn: ImageButton = brushDialog.findViewById(R.id.ib_large_brush)
        largeBtn.setOnClickListener {
            drawingView!!.setSizeForBrush(30f)
            brushDialog.dismiss()
        }
        brushDialog.show()
    }

    fun paintClicked(view: View) {
        if (view !== mImageButtonCurrentPaint) {
            val imageButton = view as ImageButton
            val colorTag = imageButton.tag.toString()
            drawingView?.setColor(colorTag)

            imageButton.setImageDrawable(
                ContextCompat.getDrawable(
                    this, R.drawable.pallet_pressed
                )
            )
            imageButton.setImageDrawable(
                ContextCompat.getDrawable(
                    this, R.drawable.pallet_normal
                )
            )
            mImageButtonCurrentPaint = view
        }
    }

    private fun showRationalDialog(title: String, message: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(title)
            .setMessage(message)
            .setPositiveButton("Cancel") {dialog, _ ->
                dialog.dismiss()
            }
        builder.create().show()
    }

    private fun getBitmapFromView(view: View): Bitmap {
        val returnedBitmap = Bitmap.createBitmap(
            view.width, view.height, Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(returnedBitmap)
        val bgDrawable = view.background

        if (bgDrawable != null) {
            bgDrawable.draw(canvas)
        } else {
            canvas.drawColor(Color.WHITE)
        }
        view.draw(canvas)
        return returnedBitmap
    }

    private fun isReadStorageAllowed(): Boolean {
        val result = ContextCompat.checkSelfPermission(
            this@MainActivity,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )

        // CHECK RESULT
        return result == PackageManager.PERMISSION_GRANTED
    }

    private suspend fun saveBitmapFile(mBitmap: Bitmap?): String {
        var result = ""
        withContext(Dispatchers.IO) {
            if (mBitmap != null) {
                try {
                    val bytes = ByteArrayOutputStream()
                    mBitmap.compress(Bitmap.CompressFormat.PNG, 90, bytes)

                    val f = File(
                        externalCacheDir!!.absoluteFile.toString()
                                + File.separator + "KidDrawingApp_"
                                + System.currentTimeMillis() / 1000 + ".png"
                    )

                    val fo = FileOutputStream(f)
                    fo.write(bytes.toByteArray())
                    fo.close()

                    result = f.absolutePath
                    runOnUiThread {
                        if (result.isNotEmpty()) {
                            Toast.makeText(
                                applicationContext,
                                "File saved successfully: $result",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            Toast.makeText(
                                applicationContext,
                                "Something went wrong while saving the file.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        // Call func cancelProgressDialog()
                        cancelProgressDialog()
                    }
                } catch (e: Exception) {
                    result = ""
                    e.printStackTrace()
                }
            }
        }
        return result
    }

    private fun saveBitmap(view : View) {
        lifecycleScope.launch {
            val bitmap = getBitmapFromView(view) // replace with your view

            // save bitmap to gallery based on Android version
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = applicationContext.contentResolver
                val contentValues = ContentValues().apply {
                    put(
                        MediaStore.Images.Media.DISPLAY_NAME,
                        "DrawingApp_${System.currentTimeMillis() / 1000}.png"
                    )
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                    put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
                }

                var imageUri: Uri? = null
                resolver.run {
                    imageUri = insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    imageUri?.let {
                        openOutputStream(it)?.use { outputStream ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                        }
                    }
                }
                if (imageUri != null) {
                    Toast.makeText(
                        this@MainActivity,
                        "File saved successfully: $imageUri", Toast.LENGTH_SHORT
                    ).show()
                    shareImage(imageUri!!)
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "Something went wrong while saving the file",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                val filePath = saveBitmapFile(bitmap)
                if (filePath.isNotEmpty()) {
                    val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                    val file = File(filePath)
                    val contentUri = Uri.fromFile(file)
                    mediaScanIntent.data = contentUri
                    sendBroadcast(mediaScanIntent)
                    Toast.makeText(
                        this@MainActivity,
                        "File saved successfully: $filePath",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "Something went wrong while saving the file",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun showProgressDialog() {
        customProgressDialog = Dialog(this@MainActivity)
        customProgressDialog!!.setContentView(R.layout.dialog_custom_progress)
        customProgressDialog!!.show()
    }

    private fun cancelProgressDialog() {
        if (customProgressDialog != null) {
            customProgressDialog!!.dismiss()
            customProgressDialog = null
        }
    }

    private fun shareImage(uri: Uri?) {
        val shareIntent = Intent()
        shareIntent.action = Intent.ACTION_SEND
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
        shareIntent.type = "image/png"
        startActivity(Intent.createChooser(shareIntent, "Share"))
    }
}