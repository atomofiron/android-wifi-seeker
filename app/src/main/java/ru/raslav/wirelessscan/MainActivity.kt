package ru.raslav.wirelessscan

import android.Manifest
import android.annotation.TargetApi
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.content.res.Configuration
import android.graphics.Point
import android.net.Uri
import android.os.Build
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import ru.raslav.wirelessscan.fragments.MainFragment
import ru.raslav.wirelessscan.fragments.PrefFragment
import ru.raslav.wirelessscan.fragments.SnapshotFragment
import ru.raslav.wirelessscan.fragments.SnapshotsListFragment

class MainActivity : AppCompatActivity() {
    companion object {
        const val ACTION_OPEN_SNAPSHOTS_LIST = "ACTION_OPEN_SNAPSHOTS_LIST"
        const val ACTION_OPEN_SNAPSHOT = "ACTION_OPEN_SNAPSHOT"
        const val EXTRA_SNAPSHOT_NAME = "EXTRA_SNAPSHOT_NAME"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        detectScreenConfigurations()

        if (granted(Manifest.permission.ACCESS_FINE_LOCATION))
            showMainFragmentIfNecessary()
        else
            requestPermission()
    }

    private fun showMainFragmentIfNecessary() {
        report("fragmentManager ${fragmentManager.findFragmentById(R.id.fragment_container)}")
        if (supportFragmentManager.fragments.isEmpty())
            supportFragmentManager.beginTransaction()
                    .add(R.id.fragment_container, MainFragment())
                    .commit()
    }

    private fun detectScreenConfigurations() {
        val size = Point()
        windowManager.defaultDisplay.getSize(size)
        Const.WIDE_MODE = size.x > size.y * 1.5 &&
                resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    private fun setFragment(fragment: Fragment) {
        supportFragmentManager.run {
            val current = fragments.findLast { it.isVisible }
            beginTransaction()
                .addToBackStack(fragment.javaClass.name)
                // todo test
                .apply { hide(current ?: return@apply) }
                .add(R.id.fragment_container, fragment)
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .commitAllowingStateLoss()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        when (intent?.action) {
            ACTION_OPEN_SNAPSHOTS_LIST -> setFragment(SnapshotsListFragment())
            ACTION_OPEN_SNAPSHOT -> setFragment(SnapshotFragment.newInstance(intent.getStringExtra(EXTRA_SNAPSHOT_NAME)!!))
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.settings -> setFragment(PrefFragment())
            R.id.mail_to_dev -> mailToDeveloper()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun mailToDeveloper() {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", "atomofiron@gmail.com", null))
                .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
                .putExtra(Intent.EXTRA_TEXT, getString(R.string.dear_dev))

        if (intent.resolveActivity(packageManager) != null)
            startActivity(
                    Intent.createChooser(intent, getString(R.string.send_email))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        else
            Toast.makeText(this, R.string.no_activity, Toast.LENGTH_SHORT).show()
    }

    private fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 7)
        else if (!granted(Manifest.permission.ACCESS_WIFI_STATE)) {
            shortToast(R.string.no_perm)
            finish()
        } else
            showMainFragmentIfNecessary()
    }

    @TargetApi(Build.VERSION_CODES.M)
    private fun showPermissionDialog() =
        AlertDialog.Builder(this)
                .setCancelable(false)
                .setMessage(R.string.need_coarse_location)
                .setPositiveButton(R.string.ok) { _, _ ->
                    requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 7)
                }.create().show()

    @TargetApi(Build.VERSION_CODES.M)
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when {
            grantResults[0] == PackageManager.PERMISSION_GRANTED -> showMainFragmentIfNecessary()
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> showPermissionDialog()
            else -> {
                shortToast(R.string.get_perm_by_settings)
                finish()
            }
        }
    }
}