package at.rtr.rmbt.android.ui.activity

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.content.*
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import android.os.Environment
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import at.rtr.rmbt.android.R
import at.rtr.rmbt.android.databinding.ActivityLoopFinishedBinding
import at.rtr.rmbt.android.di.viewModelLazy
import at.rtr.rmbt.android.viewmodel.LoopFinishedViewModel
import at.rtr.rmbt.android.viewmodel.PdfDownloadState
import at.specure.measurement.MeasurementService
import kotlin.math.max
import timber.log.Timber
import java.io.File

class LoopFinishedActivity : BaseActivity() {

    private lateinit var binding: ActivityLoopFinishedBinding
    private val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }
    private val viewModel: LoopFinishedViewModel by viewModelLazy()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = bindContentView(R.layout.activity_loop_finished)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, windowInsets ->
                val insetsSystemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                val insetsDisplayCutout = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())
                val topSafe = max(insetsSystemBars.top, insetsDisplayCutout.top)
                val leftSafe = max(insetsSystemBars.left, insetsDisplayCutout.left)
                val rightSafe = max(insetsSystemBars.right, insetsDisplayCutout.right)
                val bottomSafe = max(insetsSystemBars.bottom, insetsDisplayCutout.bottom)

                v.updatePadding(
                    right = rightSafe,
                    left = leftSafe,
                    top = topSafe,
                    bottom = bottomSafe
                )
                WindowInsetsCompat.CONSUMED
            }
        }
        binding.buttonGoToResults.setOnClickListener {
            this.finishAffinity()
            HomeActivity.startWithFragment(this, HomeActivity.Companion.HomeNavigationTarget.HISTORY_FRAGMENT_TO_SHOW)
        }

        binding.buttonRunAgain.setOnClickListener {
            this.finishAffinity()
            HomeActivity.startWithFragment(this, HomeActivity.Companion.HomeNavigationTarget.HOME_FRAGMENT_TO_SHOW)
            LoopConfigurationActivity.start(this)
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                this@LoopFinishedActivity.finishAffinity()
                HomeActivity.startWithFragment(this@LoopFinishedActivity, HomeActivity.Companion.HomeNavigationTarget.HOME_FRAGMENT_TO_SHOW)
            }
        })

        setupPdfDownload()

        binding.buttonDownloadPdfInBrowser.setOnClickListener {
            val loopUUID = intent.getStringExtra("loopUUID")
            if (!loopUUID.isNullOrBlank()) {
                Toast.makeText(this, R.string.download_pdf_starting, Toast.LENGTH_LONG).show()
                val downloadUri = viewModel.genDownloadUrl(loopUUID)
                startActivity(Intent(Intent.ACTION_VIEW, downloadUri))
            }
        }

        if (viewModel.state.isCertModeActive.get()) {
            binding.loopFinishedTitle.setText(R.string.cert_mode_finished)
            binding.buttonRunAgain.visibility = View.GONE
        } else {
            binding.buttonDownloadPdf.visibility = View.GONE
            binding.buttonDownloadPdfInBrowser.visibility = View.GONE
        }
    }

    private fun setupPdfDownload() {
        viewModel.pdfDownloadState.observe(this) { state ->
            when (state) {
                PdfDownloadState.IDLE -> {
                    binding.buttonDownloadPdf.setText(R.string.loop_download_pdf)
                    binding.buttonDownloadPdf.isEnabled = true
                }
                PdfDownloadState.REQUESTING, PdfDownloadState.DOWNLOADING -> {
                    binding.buttonDownloadPdf.setText(R.string.download_pdf_downloading)
                    binding.buttonDownloadPdf.isEnabled = false
                }
                PdfDownloadState.READY -> {
                    binding.buttonDownloadPdf.setText(R.string.open_pdf)
                    binding.buttonDownloadPdf.isEnabled = true
                }
                PdfDownloadState.ERROR -> {
                    binding.buttonDownloadPdf.setText(R.string.loop_download_pdf)
                    binding.buttonDownloadPdf.isEnabled = true
                    Toast.makeText(this, R.string.download_pdf_error, Toast.LENGTH_LONG).show()
                }
            }
        }

        viewModel.downloadFilename.observe(this) { filename ->
            downloadPdfFile(filename)
        }

        binding.buttonDownloadPdf.setOnClickListener {
            when (viewModel.pdfDownloadState.value) {
                PdfDownloadState.READY -> {
                    openDownloadedFile(baseContext, viewModel.downloadedFileId)
                }
                PdfDownloadState.IDLE, PdfDownloadState.ERROR -> {
                    val loopUUID = intent.getStringExtra("loopUUID")
                    Timber.d("Loop finished with loopUUID: %s", loopUUID)
                    if (!loopUUID.isNullOrBlank()) {
                        viewModel.requestExportPdf(loopUUID)
                    }
                }
                else -> { /* REQUESTING/DOWNLOADING — button is disabled, ignore */ }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        notificationManager.cancel(MeasurementService.NOTIFICATION_LOOP_FINISHED_ID)
        notificationManager.cancel(MeasurementService.NOTIFICATION_ID)
    }

    private var pdfDownloadComplete: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            if (DownloadManager.ACTION_DOWNLOAD_COMPLETE == intent.action) {
                val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0)
                viewModel.onDownloadComplete(downloadId)
            }
        }
    }

    private fun downloadPdfFile(filename: String?) {
        if (filename.isNullOrBlank()) {
            viewModel.onDownloadFailed()
        } else {
            val request = DownloadManager.Request(viewModel.getDownloadFileUrl(filename))
            request.setDescription(getString(R.string.download_pdf_description))
            request.setTitle(getString(R.string.download_pdf_title))
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                filename
            )

            ContextCompat.registerReceiver(
                this,
                pdfDownloadComplete,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                ContextCompat.RECEIVER_EXPORTED
            )

            val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)
        }
    }

    /**
     * Used to open the downloaded attachment.
     *
     * @param context    Content.
     * @param downloadId Id of the downloaded file to open.
     */
    @SuppressLint("Range")
    private fun openDownloadedFile(context: Context, downloadId: Long) {
        val downloadManager = context.getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query()
        query.setFilterById(downloadId)
        val cursor: Cursor = downloadManager.query(query)
        if (cursor.moveToFirst()) {
            val downloadStatus: Int = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
            val downloadLocalUri: String = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI))
            val downloadMimeType: String = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_MEDIA_TYPE))
            if (downloadStatus == DownloadManager.STATUS_SUCCESSFUL && downloadLocalUri != null) {
                openDownloadedFile(context, Uri.parse(downloadLocalUri), downloadMimeType)
            }
        }
        cursor.close()
    }

    /**
     * Used to open the downloaded attachment.
     *
     *
     * 1. Fire intent to open download file using external application.
     *
     * 2. Note:
     * 2.a. We can't share fileUri directly to other application (because we will get FileUriExposedException from Android7.0).
     * 2.b. Hence we can only share content uri with other application.
     * 2.c. We must have declared FileProvider in manifest.
     * 2.c. Refer - https://developer.android.com/reference/android/support/v4/content/FileProvider.html
     *
     * @param context            Context.
     * @param fileUri      Uri of the downloaded attachment to be opened.
     * @param attachmentMimeType MimeType of the downloaded attachment.
     */
    private fun openDownloadedFile(context: Context, fileUri: Uri?, attachmentMimeType: String) {
        var finalUri: Uri? = fileUri
        if (fileUri != null) {
            if (ContentResolver.SCHEME_FILE.equals(fileUri.scheme)) {
                val file = File(fileUri.path.orEmpty())
                finalUri = FileProvider.getUriForFile(this, "cz.ctu.measurement.cert", file)
            }
            val openAttachmentIntent = Intent(Intent.ACTION_VIEW)
            openAttachmentIntent.setDataAndType(finalUri, attachmentMimeType)
            openAttachmentIntent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            try {
                startActivity(openAttachmentIntent)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(
                    context,
                    R.string.cannot_open_file,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(pdfDownloadComplete)
        } catch (_: IllegalArgumentException) {
            // Receiver was not registered — nothing to unregister
        }
        super.onDestroy()
    }

    companion object {

        fun start(context: Context) = context.startActivity(Intent(context, LoopFinishedActivity::class.java))
        fun startForCertMode(context: Context, loopUUID: String) = context.startActivity(Intent(context, LoopFinishedActivity::class.java).apply {
            putExtra("loopUUID", loopUUID)
        })
    }
}
