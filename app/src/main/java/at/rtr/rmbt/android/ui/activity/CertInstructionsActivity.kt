package at.rtr.rmbt.android.ui.activity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.PersistableBundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewpager.widget.PagerAdapter
import at.rtr.rmbt.android.R
import at.rtr.rmbt.android.databinding.ActivityCertInstructionsBinding
import at.rtr.rmbt.android.databinding.ViewLoopModeInstructionBinding
import at.rtr.rmbt.android.di.viewModelLazy
import at.rtr.rmbt.android.viewmodel.CertConfigurationViewModel

class CertInstructionsActivity : BaseActivity(), CertInstructionsCallback {

    private lateinit var binding: ActivityCertInstructionsBinding
    private val viewModel: CertConfigurationViewModel by viewModelLazy()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = bindContentView(R.layout.activity_cert_instructions)
        binding.certInstTitle.text = getString(R.string.title_cert_instruction_1)

        binding.certInstPager.adapter = InstructionsAdapter(this, this);
    }

    private val PAGE_COUNT = 3;

    private inner class InstructionsAdapter(context: Context, private val callback: CertInstructionsCallback) : PagerAdapter() {

        private var items = listOf(
            Html.fromHtml(context.getString(R.string.text_cert_instruction_1)),
            Html.fromHtml(context.getString(R.string.text_cert_instruction_2)),
            Html.fromHtml(context.getString(R.string.text_cert_instruction_3))
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