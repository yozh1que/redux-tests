package dfilyustin.translate.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import dfilyustin.translate.R
import dfilyustin.translate.model.Translation
import kotlinx.android.synthetic.main.activity_meaning.*

class MeaningActivity : AppCompatActivity() {

    companion object {
        private const val meaningKey = "meaning"
        fun intent(context: Context, meaning: Translation.Meaning) =
            Intent(context, MeaningActivity::class.java).putExtra(
                meaningKey, meaning
            )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        setContentView(R.layout.activity_meaning)

        intent.getParcelableExtra<Translation.Meaning>(meaningKey).apply {
            meaningTextView.text = translatedText
            Glide.with(meaningImageView).load(imageUrl).into(meaningImageView)
        }

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
        }
        return super.onOptionsItemSelected(item)
    }
}