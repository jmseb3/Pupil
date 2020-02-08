/*
 *     Pupil, Hitomi.la viewer for Android
 *     Copyright (C) 2019  tom5079
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package xyz.quaver.pupil.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.settings_activity.*
import kotlinx.serialization.ImplicitReflectionSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.parseList
import net.rdrei.android.dirchooser.DirectoryChooserActivity
import xyz.quaver.pupil.Pupil
import xyz.quaver.pupil.R
import xyz.quaver.pupil.ui.fragment.LockFragment
import xyz.quaver.pupil.ui.fragment.SettingsFragment
import xyz.quaver.pupil.util.REQUEST_DOWNLOAD_FOLDER
import xyz.quaver.pupil.util.REQUEST_DOWNLOAD_FOLDER_OLD
import xyz.quaver.pupil.util.REQUEST_LOCK
import xyz.quaver.pupil.util.REQUEST_RESTORE
import java.io.File
import java.nio.charset.Charset

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE)

        setContentView(R.layout.settings_activity)
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings, SettingsFragment())
            .commit()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onResume() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)

        if (preferences.getBoolean("security_mode", false))
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE)
        else
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        super.onResume()
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            android.R.id.home -> onBackPressed()
        }

        return true
    }

    @UseExperimental(ImplicitReflectionSerializer::class)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when(requestCode) {
            REQUEST_LOCK -> {
                if (resultCode == Activity.RESULT_OK) {
                    supportFragmentManager
                        .beginTransaction()
                        .replace(R.id.settings, LockFragment())
                        .addToBackStack("Lock")
                        .commitAllowingStateLoss()
                }
            }
            REQUEST_RESTORE -> {
                if (resultCode == Activity.RESULT_OK) {
                    val uri = data?.data ?: return

                    try {
                        val json = contentResolver.openInputStream(uri).use { inputStream ->
                            inputStream!!

                            inputStream.readBytes().toString(Charset.defaultCharset())
                        }

                        (application as Pupil).favorites.addAll(Json.parseList<Int>(json).also {
                            Snackbar.make(
                                window.decorView,
                                getString(R.string.settings_restore_successful, it.size),
                                Snackbar.LENGTH_LONG
                            ).show()
                        })
                    } catch (e: Exception) {
                        Snackbar.make(
                            window.decorView,
                            R.string.settings_restore_failed,
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                }
            }
            REQUEST_DOWNLOAD_FOLDER -> {
                if (resultCode == Activity.RESULT_OK) {
                    data?.data?.also { uri ->
                        val takeFlags: Int = intent.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
                            contentResolver.takePersistableUriPermission(uri, takeFlags)

                        if (DocumentFile.fromTreeUri(this, uri)?.canWrite() == false)
                            Snackbar.make(settings, R.string.settings_dl_location_not_writable, Snackbar.LENGTH_LONG).show()
                        else
                            PreferenceManager.getDefaultSharedPreferences(this).edit()
                                .putString("dl_location", uri.toString())
                                .apply()
                    }
                }
            }
            REQUEST_DOWNLOAD_FOLDER_OLD -> {
                if (resultCode == DirectoryChooserActivity.RESULT_CODE_DIR_SELECTED) {
                    val directory = data?.getStringExtra(DirectoryChooserActivity.RESULT_SELECTED_DIR)!!

                    if (!File(directory).canWrite())
                        Snackbar.make(settings, R.string.settings_dl_location_not_writable, Snackbar.LENGTH_LONG).show()
                    else
                        PreferenceManager.getDefaultSharedPreferences(this).edit()
                            .putString("dl_location", Uri.fromFile(File(directory)).toString())
                            .apply()
                }
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }
}