package at.rtr.rmbt.android.ui.activity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.viewpager.widget.PagerAdapter
import at.rtr.rmbt.android.R
import at.rtr.rmbt.android.databinding.ActivityCertInstructionsBinding
import at.rtr.rmbt.android.databinding.ViewLoopModeInstructionBinding
import at.rtr.rmbt.android.di.viewModelLazy
import at.rtr.rmbt.android.viewmodel.CertConfigurationViewModel
import kotlin.math.max

class CertInstructionsActivity : BaseActivity(), CertInstructionsCallback {

    private lateinit var binding: ActivityCertInstructionsBinding
    private val viewModel: CertConfigurationViewModel by viewModelLazy()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = bindContentView(R.layout.activity_cert_instructions)

        // correctly fit inside display
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

        binding.certInstTitle.text = getString(R.string.title_cert_instruction_1)

        binding.certInstPager.adapter = InstructionsAdapter(this, this);
    }

    private val PAGE_COUNT = 3;

    private inner class InstructionsAdapter(context: Context, private val callback: CertInstructionsCallback) : PagerAdapter() {

        private var items = listOf(
            Html.fromHtml(context.getString(R.string.text_cert_instruction_1), Html.FROM_HTML_SEPARATOR_LINE_BREAK_LIST),
            Html.fromHtml(context.getString(R.string.text_cert_instruction_2), Html.FROM_HTML_SEPARATOR_LINE_BREAK_LIST),
            Html.fromHtml(context.getString(R.string.text_cert_instruction_3), Html.FROM_HTML_SEPARATOR_LINE_BREAK_LIST)
        )

        override fun isViewFromObject(view: View, o: Any) = view == o
        override fun getCount() = PAGE_COUNT

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val binding = ViewLoopModeInstructionBinding.inflate(LayoutInflater.from(container.context))

            binding.content.text = items[position]

            binding.decline.setOnClickListener { callback.onDeclined() }
            binding.accept.setOnClickListener {
                when(position) {
                    0 -> callback.onFirstPageAccepted()
                    1 -> callback.onSecondPageAccepted()
                    2 -> callback.onThirdPageAccepted()
                }
            }
            container.addView(binding.root)
            return binding.root
        }

        override fun destroyItem(container: ViewGroup, position: Int, o: Any) {
            container.removeView(o as View)
        }
    }

    override fun onDeclined() {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    override fun onFirstPageAccepted() {
        binding.certInstTitle.text = getString(R.string.title_cert_instruction_2)
        binding.certInstPager.setCurrentItem(1, true)
    }

    override fun onSecondPageAccepted() {
        binding.certInstTitle.text = getString(R.string.title_cert_instruction_3)
        binding.certInstPager.setCurrentItem(2, true)
    }

    override fun onThirdPageAccepted() {
        setResult(Activity.RESULT_OK)
        finish()
    }

    companion object {
        fun start(context: Context): Intent = Intent(context, CertInstructionsActivity::class.java)
    }
}

interface CertInstructionsCallback {
    fun onDeclined()
    fun onFirstPageAccepted()
    fun onSecondPageAccepted()
    fun onThirdPageAccepted()
}