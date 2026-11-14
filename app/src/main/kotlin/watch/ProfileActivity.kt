package watch

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Collects the buyer's details. They are stored encrypted on this phone only. */
class ProfileActivity : Activity() {
    companion object {
        const val ID_NAME = 9201
        const val ID_ID = 9202
        const val ID_PHONE = 9203
        const val ID_EMAIL = 9204
        const val ID_MALE = 9205
        const val ID_FEMALE = 9206
        const val ID_BIRTH = 9207
        const val ID_AGE = 9208
        const val ID_CITY = 9209
        const val ID_FACEBOOK = 9210
        const val ID_INSTAGRAM = 9211
        const val ID_SMOKER_YES = 9212
        const val ID_SMOKER_NO = 9213
        const val ID_TICKETS = 9214
        const val ID_SAVE = 9215
        const val ID_PROBLEMS = 9216

        var boxOverride: SecretBox?
            get() = SecretBox.override
            set(v) { SecretBox.override = v }
    }

    private lateinit var store: ProfileStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ProfileStore.forContext(this)
        val old = store.load() ?: Profile()
        val pad = Ui.dp(this, 20)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad * 2) }

        col.addView(Ui.text(this, "Buyer details", 26f, bold = true))
        col.addView(Ui.text(this, "Used to fill the checkout. Stored encrypted on this phone only; never uploaded.", 13f, Ui.MUTED)
            .apply { setPadding(0, Ui.dp(context, 4), 0, Ui.dp(context, 8)) })

        field(col, ID_NAME, "Full name (as on the ID, Hebrew is fine)", old.fullName, InputType.TYPE_CLASS_TEXT)
        field(col, ID_ID, "ID number", old.idNumber, InputType.TYPE_CLASS_NUMBER)
        field(col, ID_PHONE, "Phone", old.phone, InputType.TYPE_CLASS_PHONE)
        field(col, ID_EMAIL, "Email", old.email, InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS or InputType.TYPE_CLASS_TEXT)

        col.addView(label("Gender (required by the form)"))
        col.addView(RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            addView(radio(ID_FEMALE, "Female", old.gender == Gender.FEMALE))
            addView(radio(ID_MALE, "Male", old.gender == Gender.MALE))
        })

        field(col, ID_BIRTH, "Birth date – just type the digits, e.g. 05041986 (only if an event asks)", old.birthDate, InputType.TYPE_CLASS_NUMBER)
        field(col, ID_AGE, "Age (only if an event asks)", old.age?.toString() ?: "", InputType.TYPE_CLASS_NUMBER)
        field(col, ID_CITY, "City / area of residence", old.city, InputType.TYPE_CLASS_TEXT)
        field(col, ID_FACEBOOK, "Facebook profile link", old.facebookUrl, InputType.TYPE_TEXT_VARIATION_URI or InputType.TYPE_CLASS_TEXT)
        field(col, ID_INSTAGRAM, "Instagram profile link", old.instagramUrl, InputType.TYPE_TEXT_VARIATION_URI or InputType.TYPE_CLASS_TEXT)

        col.addView(label("Smoker? (answers the \"מעשנים?\" question; leave empty to answer it yourself)"))
        col.addView(RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            addView(radio(ID_SMOKER_YES, "Yes", old.smoker == true))
            addView(radio(ID_SMOKER_NO, "No", old.smoker == false))
        })

        field(col, ID_TICKETS, "Number of tickets (1-10)", old.ticketCount.toString(), InputType.TYPE_CLASS_NUMBER)

        col.addView(Ui.text(this, "", 14f, Ui.AMBER).apply { id = ID_PROBLEMS; setPadding(0, Ui.dp(context, 12), 0, 0) })
        col.addView(Ui.pill(this, "Save", Ui.ACCENT, 0xFFFFFFFF.toInt(), 17f).apply {
            id = ID_SAVE
            setOnClickListener { save() }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.dp(this@ProfileActivity, 12) })

        setContentView(ScrollView(this).apply { setBackgroundColor(Ui.BG); addView(col) })

        // The number keyboard has no dash key, so the dashes are added as the digits are typed.
        val birth = findViewById<EditText>(ID_BIRTH)
        // Numeric keyboard, but a key listener that also accepts '-', otherwise Android strips our dashes.
        birth.keyListener = android.text.method.DigitsKeyListener.getInstance("0123456789-/.")
        birth.addTextChangedListener(object : android.text.TextWatcher {
            private var busy = false
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) = Unit
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable) {
                if (busy) return
                val formatted = BirthDate.typing(BirthDate.normalize(s.toString()))
                if (formatted != s.toString()) {
                    busy = true
                    try { s.replace(0, s.length, formatted) } finally { busy = false }
                }
            }
        })
    }

    private fun save() {
        val p = Profile(
            fullName = text(ID_NAME).trim(),
            idNumber = text(ID_ID).trim(),
            phone = text(ID_PHONE).trim(),
            email = text(ID_EMAIL).trim(),
            gender = when {
                findViewById<RadioButton>(ID_FEMALE).isChecked -> Gender.FEMALE
                findViewById<RadioButton>(ID_MALE).isChecked -> Gender.MALE
                else -> null
            },
            birthDate = BirthDate.normalize(text(ID_BIRTH)),
            age = text(ID_AGE).trim().toIntOrNull(),
            city = text(ID_CITY).trim(),
            facebookUrl = text(ID_FACEBOOK).trim(),
            instagramUrl = text(ID_INSTAGRAM).trim(),
            smoker = when {
                findViewById<RadioButton>(ID_SMOKER_YES).isChecked -> true
                findViewById<RadioButton>(ID_SMOKER_NO).isChecked -> false
                else -> null
            },
            ticketCount = text(ID_TICKETS).trim().toIntOrNull() ?: 0,
        )
        val problems = p.problems()
        findViewById<TextView>(ID_PROBLEMS).text = problems.joinToString("\n") { "• $it" }
        if (problems.isEmpty()) {
            try {
                store.save(p)
                if (store.load() != p) { // prove it can be read back before telling the user it is saved
                    findViewById<TextView>(ID_PROBLEMS).text = "• Saved, but the details could not be read back. Please try again."
                    return
                }
            } catch (e: Exception) {
                findViewById<TextView>(ID_PROBLEMS).text = "• Could not save: ${e.javaClass.simpleName} ${e.message.orEmpty()}"
                return
            }
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun text(id: Int) = findViewById<EditText>(id).text.toString()

    private fun label(s: String): View = Ui.text(this, s, 13f, Ui.MUTED).apply { setPadding(0, Ui.dp(context, 14), 0, Ui.dp(context, 4)) }

    private fun radio(id: Int, s: String, checked: Boolean) = RadioButton(this).apply {
        this.id = id
        text = s
        setTextColor(Ui.TEXT)
        isChecked = checked
    }

    private fun field(parent: LinearLayout, id: Int, hint: String, value: String, type: Int) {
        parent.addView(label(hint))
        parent.addView(EditText(this).apply {
            this.id = id
            setText(value)
            inputType = type
            setTextColor(Ui.TEXT)
            textDirection = View.TEXT_DIRECTION_ANY_RTL
            textAlignment = View.TEXT_ALIGNMENT_TEXT_START
            background = Ui.round(context, Ui.CARD, 12)
            setPadding(Ui.dp(context, 14), Ui.dp(context, 12), Ui.dp(context, 14), Ui.dp(context, 12))
        }, LinearLayout.LayoutParams(-1, -2))
    }
}
