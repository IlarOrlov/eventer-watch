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

        field(col, ID_BIRTH, "Birth date dd-mm-yyyy (only if an event asks)", old.birthDate, InputType.TYPE_CLASS_DATETIME)
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
            birthDate = text(ID_BIRTH).trim(),
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
            store.save(p)
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
