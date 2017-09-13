package io.atomofiron.wirelessscan

import android.Manifest
import android.annotation.TargetApi
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import io.atomofiron.wirelessscan.fragments.MainFragment
import android.content.res.Configuration
import android.graphics.Point
import android.net.Uri
import android.os.Build
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import io.atomofiron.wirelessscan.fragments.PrefFragment
import io.atomofiron.wirelessscan.fragments.SnapshotFragment
import io.atomofiron.wirelessscan.fragments.SnapshotsListFragment

class MainActivity : Activity() {
    companion object {
        val ACTION_OPEN_SNAPSHOTS_LIST = "ACTION_OPEN_SNAPSHOTS_LIST"
        val ACTION_OPEN_SNAPSHOT = "ACTION_OPEN_SNAPSHOT"
        val EXTRA_SNAPSHOT_NAME = "EXTRA_SNAPSHOT_NAME"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        detectScreenConfigurations()

        if (I.granted(this, Manifest.permission.ACCESS_COARSE_LOCATION))
            showMainFragmentIfNecessary()
        else
            requestPermission()
    }

    private fun showMainFragmentIfNecessary() {
        if (fragmentManager.findFragmentById(R.id.fragment_container) == null)
            fragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, MainFragment() as Fragment)
                    .commitAllowingStateLoss()
    }

    private fun detectScreenConfigurations() {
        val size = Point()
        windowManager.defaultDisplay.getSize(size)
        I.WIDE_MODE = size.x > size.y * 1.5 &&
                resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    private fun setFragment(fragment: Fragment) {
        fragmentManager.beginTransaction()
                .addToBackStack(fragment.javaClass.name)
                .replace(R.id.fragment_container, fragment)
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .commitAllowingStateLoss()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        when (intent?.action) {
            ACTION_OPEN_SNAPSHOTS_LIST -> setFragment(SnapshotsListFragment())
            ACTION_OPEN_SNAPSHOT -> setFragment(SnapshotFragment.newInstance(intent.getStringExtra(EXTRA_SNAPSHOT_NAME)))
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION))
                showPermissionDialog()
            else {
                I.shortToast(this, R.string.get_perm_by_settings)
                finish()
            }
        } else if (!I.granted(this, Manifest.permission.ACCESS_WIFI_STATE)) {
            I.shortToast(this, R.string.no_perm)
            finish()
        } else
            showMainFragmentIfNecessary()
    }

    @TargetApi(Build.VERSION_CODES.M)
    private fun showPermissionDialog() =
        AlertDialog.Builder(this)
                .setCancelable(false)
                .setMessage(R.string.need_coarse_location)
                .setPositiveButton(R.string.ok, { _, _ ->
                    requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 7)
                }).create().show()

    @TargetApi(Build.VERSION_CODES.M)
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when {
            grantResults[0] == PackageManager.PERMISSION_GRANTED -> showMainFragmentIfNecessary()
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION) -> showPermissionDialog()
            else -> {
                I.shortToast(this, R.string.get_perm_by_settings)
                finish()
            }
        }
    }
}