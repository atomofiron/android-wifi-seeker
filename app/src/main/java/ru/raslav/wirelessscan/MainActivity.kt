package ru.raslav.wirelessscan

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import lib.atomofiron.insets.InsetsProviderImpl
import lib.atomofiron.insets.insetsPadding
import lib.atomofiron.insets.setContentView
import ru.raslav.wirelessscan.fragments.MainFragment
import ru.raslav.wirelessscan.fragments.PrefFragment
import ru.raslav.wirelessscan.fragments.SnapshotFragment
import ru.raslav.wirelessscan.fragments.SnapshotListFragment
import ru.raslav.wirelessscan.fragments.Titled


class MainActivity : AppCompatActivity() {
    companion object {
        const val ACTION_OPEN_SNAPSHOTS_LIST = "ACTION_OPEN_SNAPSHOTS_LIST"
        const val ACTION_OPEN_SNAPSHOT = "ACTION_OPEN_SNAPSHOT"
        const val EXTRA_SNAPSHOT_NAME = "EXTRA_SNAPSHOT_NAME"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentView(R.layout.activity_main, InsetsProviderImpl())
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_back)
        findViewById<View>(R.id.app_bar).insetsPadding(start = true, top = true, end = true)

        supportFragmentManager.addOnBackStackChangedListener {
            updateTitle()
        }
        if (supportFragmentManager.fragments.isEmpty())
            supportFragmentManager.beginTransaction()
                    .add(R.id.fragment_container, MainFragment())
                    .commit()
    }

    private fun setFragment(fragment: Fragment) {
        supportFragmentManager.run {
            val current = fragments.findLast { it.isVisible }
            beginTransaction()
                .addToBackStack(fragment.javaClass.name)
                .apply { hide(current ?: return@apply) }
                .add(R.id.fragment_container, fragment)
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE)
                .commit()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        when (intent.action) {
            ACTION_OPEN_SNAPSHOTS_LIST -> setFragment(SnapshotListFragment())
            ACTION_OPEN_SNAPSHOT -> setFragment(SnapshotFragment.newInstance(intent.getStringExtra(EXTRA_SNAPSHOT_NAME)!!))
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> supportFragmentManager.popBackStack()
            R.id.settings -> setFragment(PrefFragment())
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onResume() {
        super.onResume()
        updateTitle()
    }

    private fun updateTitle() {
        supportActionBar?.setDisplayHomeAsUpEnabled(supportFragmentManager.backStackEntryCount > 0)
        val current = supportFragmentManager.fragments.findLast { it.isVisible }
        current ?: return
        current as Titled
        title = current.title
            ?: resources.takeIf { current.titleId > 0 }
                ?.getString(current.titleId)
                    ?: ""
    }
}