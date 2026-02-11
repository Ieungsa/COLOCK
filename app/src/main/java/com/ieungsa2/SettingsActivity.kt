package com.ieungsa2

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView

class SettingsActivity : BaseActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var menuButton: ImageView
    private lateinit var fontSizeSeekBar: SeekBar
    private lateinit var fontPreviewText: TextView
    private lateinit var termsCard: CardView
    private lateinit var privacyCard: CardView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        initViews()
        setupClickListeners()
        setupDrawer()
        setupFontSizeSeekBar()
        setupBackPressHandler()
    }

    private fun initViews() {
        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.navigation_view)
        menuButton = findViewById(R.id.menu_button)
        fontSizeSeekBar = findViewById(R.id.font_size_seekbar)
        fontPreviewText = findViewById(R.id.font_preview_text)
        termsCard = findViewById(R.id.terms_card)
        privacyCard = findViewById(R.id.privacy_card)
    }

    private fun setupFontSizeSeekBar() {
        val sharedPref = getSharedPreferences("app_settings", MODE_PRIVATE)
        val savedProgress = sharedPref.getInt("font_scale_progress", 0)
        fontSizeSeekBar.progress = savedProgress
        updatePreviewFont(savedProgress)

        fontSizeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updatePreviewFont(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val progress = seekBar?.progress ?: 0
                sharedPref.edit().putInt("font_scale_progress", progress).apply()
                Toast.makeText(this@SettingsActivity, "글자 크기가 설정되었습니다. (앱 재시작 시 전체 적용)", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun updatePreviewFont(progress: Int) {
        val size = when(progress) {
            1 -> 20f
            2 -> 26f
            else -> 14f
        }
        fontPreviewText.textSize = size
    }

    private fun setupClickListeners() {
        termsCard.setOnClickListener {
            showTermsDialog("이용약관", "제 1 조 (목적)\n본 약관은 COLOCK 서비스의 이용 조건 및 절차를 규정함을 목적으로 합니다.\n\n제 2 조 (용어의 정의)\n1. '서비스'라 함은 회사가 제공하는 스미싱 및 보이스피싱 예방 솔루션을 의미합니다.\n2. '사용자'라 함은 본 약관에 따라 서비스를 이용하는 자를 의미합니다.\n\n제 3 조 (서비스의 범위)\n회사는 사용자에게 문자 메시지 분석, 통화 분석 및 상호 인증 기능을 제공합니다.")
        }

        privacyCard.setOnClickListener {
            showTermsDialog("개인정보 처리방침", "COLOCK은 사용자의 개인정보를 보호하기 위해 최선을 다합니다.\n\n1. 수집 항목: 전화번호, 기기 식별 정보, 문자 메시지 수신 내용(분석용).\n2. 이용 목적: 스미싱 및 보이스피싱 탐지.\n3. 보유 기간: 서비스 이용 종료 시까지 또는 법령이 정한 기간까지.")
        }
    }

    private fun showTermsDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("확인") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun setupDrawer() {
        menuButton.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.END)
        }

        navigationView.setNavigationItemSelectedListener { menuItem ->
            drawerLayout.closeDrawer(GravityCompat.END)
            when (menuItem.itemId) {
                R.id.nav_home -> {
                    val intent = Intent(this, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                    startActivity(intent)
                    finish()
                    true
                }
                R.id.nav_vishing -> {
                    startActivity(Intent(this, VishingActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_smishing -> {
                    startActivity(Intent(this, SmishingActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_family_verify -> {
                    startActivity(Intent(this, FamilyVerifyActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_settings -> true
                R.id.nav_about -> {
                    showAboutDialog()
                    true
                }
                else -> false
            }
        }
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle("COLOCK 정보")
            .setMessage("버전: 2.0 (통합)\n© 2026 KMOU Capstone Team")
            .setPositiveButton("확인", null)
            .show()
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.END)) {
                    drawerLayout.closeDrawer(GravityCompat.END)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }
}