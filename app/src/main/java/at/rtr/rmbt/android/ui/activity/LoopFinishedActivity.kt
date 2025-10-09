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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import android.os.Environment
import android.view.View
import android.widget.Toast
import androidx.core.content.FileProvider
import at.rmbt.client.control.ExportPdfResponse
import at.rmbt.client.control.ExportRequestBody
import at.rtr.rmbt.android.R
import at.rtr.rmbt.android.databinding.ActivityLoopFinishedBinding
import at.rtr.rmbt.android.di.viewModelLazy
import at.rtr.rmbt.android.viewmodel.LoopFinishedViewModel
import at.specure.measurement.MeasurementService
import kotlin.math.max
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
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

        binding.buttonDownloadPdf.setOnClickListener {
            val loopUUID = intent.getStringExtra("loopUUID")
            Timber.d("Loop finished with loopUUID: %s", loopUUID)

            if(!loopUUID.isNullOrBlank()) {
                Toast.makeText(this, R.string.download_pdf_starting, Toast.LENGTH_LONG).show()
                viewModel.getExportPdf(ExportRequestBody(loopUUID = loopUUID), pdfUrlCallback)
            }

        }

        binding.buttonDownloadPdfInBrowser.setOnClickListener {
            val loopUUID = intent.getStringExtra("loopUUID")
            if(!loopUUID.isNullOrBlank()) {
                Toast.makeText(this, R.string.download_pdf_starting, Toast.LENGTH_LONG).show()
                val downloadUri = viewModel.genDownloadUrl(loopUUID)
                startActivity(Intent(Intent.ACTION_VIEW, downloadUri))
            }
        }

        if(viewModel.state.isCertModeActive.get()) {
            binding.loopFinishedTitle.setText(R.string.cert_mode_finished)
            binding.buttonRunAgain.visibility = View.GONE

            viewModel.resetLoopMode()
        } else {
            binding.buttonDownloadPdf.visibility = View.GONE
            binding.buttonDownloadPdfInBrowser.visibility = View.GONE
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        this.finishAffinity()
        HomeActivity.startWithFragment(this, HomeActivity.Companion.HomeNavigationTarget.HOME_FRAGMENT_TO_SHOW)
    }

    override fun onResume() {
        super.onResume()
        notificationManager.cancel(MeasurementService.NOTIFICATION_LOOP_FINISHED_ID)
        notificationManager.cancel(MeasurementService.NOTIFICATION_ID)
    }

    private var pdfDownloadComplete: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            val action = intent.action
            if (DownloadManager.ACTION_DOWNLOAD_COMPLETE == action) {
                val downloadId = intent.getLongExtra(
                    DownloadManager.EXTRA_DOWNLOAD_ID, 0
                )
                openDownloadedFile(baseContext, downloadId)
            }
        }
    }

    private val pdfUrlCallback: Callback<ExportPdfResponse> = object : Callback<ExportPdfResponse> {
        override fun onResponse(
            call: Call<ExportPdfResponse>,
            response: Response<ExportPdfResponse>
        ) {
            if (response.isSuccessful) {
                Timber.d("Export response OK, file: %s", response.body()?.file)
//                viewModel.state.exportPdfFileName.set(response.body()?.file)
                downloadPdfFile(response.body()?.file)
            } else {
                Timber.d("Export response failed, msg: %s", response.message())
            }

        }

        override fun onFailure(call: Call<ExportPdfResponse>, t: Throwable) {
            Timber.d("Export response failed, exception: %s", t.message)
            Timber.d(t)
        }

    }

    private fun downloadPdfFile(filename: String?) {
        if(filename.isNullOrBlank()) {
            Toast.makeText(this, R.string.download_pdf_error, Toast.LENGTH_LONG).show()
        } else {
            val request = DownloadManager.Request(viewModel.getDownloadFileUrl(filename))
            request.setDescription(getString(R.string.download_pdf_description))
            request.setTitle(getString(R.string.download_pdf_title))
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                filename
            )

            registerReceiver(
                pdfDownloadComplete,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
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
            // Get Content Uri.
            if (ContentResolver.SCHEME_FILE.equals(fileUri.scheme)) {
                // FileUri - Convert it to contentUri.
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
                    "Nelze otevřít soubor",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    companion object {

        fun start(context: Context) = context.startActivity(Intent(context, LoopFinishedActivity::class.java))
        fun startForCertMode(context: Context, loopUUID: String) = context.startActivity(Intent(context, LoopFinishedActivity::class.java).apply {
            putExtra("loopUUID", loopUUID)
        })
    }
}