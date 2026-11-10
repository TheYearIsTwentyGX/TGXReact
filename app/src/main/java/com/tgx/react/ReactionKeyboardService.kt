package com.tgx.react

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Outline
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * The TGXReact keyboard: a grid of the user's reaction images. Tapping one inserts it into the
 * text field as rich content (it is never sent — no editor action is performed). Apps that don't
 * accept images get it on the clipboard instead.
 */
class ReactionKeyboardService : InputMethodService() {
    private lateinit var adapter: Adapter
    private lateinit var emptyView: TextView
    private lateinit var hintView: TextView
    private val handler = Handler(Looper.getMainLooper())

    private fun dp(v: Int) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ContextCompat.getColor(context, R.color.kb_bg))
        }

        hintView = TextView(this).apply {
            setTextColor(0xFFB8B0CC.toInt())
            textSize = 12f
            setPadding(dp(12), dp(6), dp(12), 0)
            text = "This app doesn't accept images — tapping copies to clipboard"
            visibility = View.GONE
        }
        root.addView(hintView)

        val gridHolder = FrameLayout(this)
        val span = (resources.displayMetrics.widthPixels / dp(92)).coerceAtLeast(3)
        adapter = Adapter((resources.displayMetrics.widthPixels / span)) { insert(it) }
        val grid = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(context, span)
            adapter = this@ReactionKeyboardService.adapter
            setPadding(dp(4), dp(4), dp(4), dp(4))
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        gridHolder.addView(grid, FrameLayout.LayoutParams(-1, -1))
        emptyView = TextView(this).apply {
            text = "No reactions yet.\nTap ✎ below to add some."
            gravity = Gravity.CENTER
            setTextColor(0xFFB8B0CC.toInt())
            textSize = 15f
        }
        gridHolder.addView(emptyView, FrameLayout.LayoutParams(-1, -1))
        root.addView(gridHolder, LinearLayout.LayoutParams(-1, dp(250)))

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(ContextCompat.getColor(context, R.color.kb_bar))
            setPadding(dp(6), 0, dp(6), 0)
        }
        fun button(icon: Int, desc: String) = ImageButton(this).apply {
            setImageResource(icon)
            contentDescription = desc
            setBackgroundResource(R.drawable.kb_button_bg)
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        }
        val globe = button(R.drawable.ic_kb_globe, "Switch keyboard").apply {
            setOnClickListener { switchKeyboard() }
            setOnLongClickListener {
                getSystemService(InputMethodManager::class.java).showInputMethodPicker(); true
            }
        }
        val edit = button(R.drawable.ic_kb_edit, "Manage reactions").apply {
            setOnClickListener {
                startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        val title = TextView(this).apply {
            text = "TGXReact"
            setTextColor(0xFF8F86A8.toInt())
            textSize = 13f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val backspace = button(R.drawable.ic_kb_backspace, "Backspace").apply { setupRepeatingDelete(this) }
        bar.addView(globe); bar.addView(edit); bar.addView(title); bar.addView(backspace)
        root.addView(bar, LinearLayout.LayoutParams(-1, dp(52)))
        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val items = ReactionRepository.items.value
        adapter.submit(items)
        emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        val acceptsImages = info != null && EditorInfoCompat.getContentMimeTypes(info)
            .any { ClipDescription.compareMimeTypes("image/png", it) || ClipDescription.compareMimeTypes("image/gif", it) }
        hintView.visibility = if (acceptsImages || items.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun switchKeyboard() {
        if (!switchToPreviousInputMethod() && !switchToNextInputMethod(false)) {
            getSystemService(InputMethodManager::class.java).showInputMethodPicker()
        }
    }

    private fun setupRepeatingDelete(v: View) {
        val repeat = object : Runnable {
            override fun run() { deleteOne(); handler.postDelayed(this, 60) }
        }
        v.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.isPressed = true
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    deleteOne(); handler.postDelayed(repeat, 400)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.isPressed = false
                    handler.removeCallbacks(repeat)
                    if (e.actionMasked == MotionEvent.ACTION_UP) view.performClick()
                }
            }
            true
        }
    }

    private fun deleteOne() {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) ic.commitText("", 1) else sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
    }

    /** Inserts [r] into the current field. Never sends: only commitContent, no editor action. */
    private fun insert(r: Reaction) {
        val file = ReactionRepository.fileFor(r)
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        val ei = currentInputEditorInfo
        val ic = currentInputConnection
        val accepted = ei != null && ic != null &&
            EditorInfoCompat.getContentMimeTypes(ei).any { ClipDescription.compareMimeTypes(r.mime, it) }
        if (accepted) {
            val info = InputContentInfoCompat(uri, ClipDescription("Reaction", arrayOf(r.mime)), null)
            val ok = InputConnectionCompat.commitContent(
                ic!!, ei!!, info, InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null
            )
            if (ok) return
        }
        val cm = getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newUri(contentResolver, "Reaction", uri))
        Toast.makeText(this, "Copied — long-press the text field to paste", Toast.LENGTH_SHORT).show()
    }

    private class Adapter(private val cellPx: Int, private val onTap: (Reaction) -> Unit) :
        RecyclerView.Adapter<Adapter.Holder>() {
        private var items: List<Reaction> = emptyList()

        @android.annotation.SuppressLint("NotifyDataSetChanged")
        fun submit(list: List<Reaction>) {
            if (list == items) return
            items = list; notifyDataSetChanged()
        }

        class Holder(val image: ImageView) : RecyclerView.ViewHolder(image)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val m = (parent.resources.displayMetrics.density * 3).toInt()
            val iv = ImageView(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(-1, cellPx - 2 * m).apply { setMargins(m, m, m, m) }
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundResource(R.drawable.kb_cell_bg)
                clipToOutline = true
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) =
                        outline.setRoundRect(0, 0, view.width, view.height, 10 * parent.resources.displayMetrics.density)
                }
            }
            return Holder(iv)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val r = items[position]
            ThumbnailLoader.load(holder.image, ReactionRepository.fileFor(r), cellPx)
            holder.image.contentDescription = "Reaction ${position + 1}"
            holder.image.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onTap(r)
            }
        }

        override fun getItemCount() = items.size
    }
}
