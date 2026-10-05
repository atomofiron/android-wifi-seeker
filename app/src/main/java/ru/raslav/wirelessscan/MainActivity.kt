package ru.raslav.wirelessscan

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import lib.atomofiron.insets.InsetsProviderImpl
import lib.atomofiron.insets.setContentView
import ru.raslav.wirelessscan.fragments.MainFragment
import ru.raslav.wirelessscan.fragments.PreferenceFragment
import ru.raslav.wirelessscan.fragments.SnapshotFragment
import ru.raslav.wirelessscan.fragments.SnapshotListFragment

fun FragmentActivity.asMain(): MainActivity = this as MainActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentView(R.layout.activity_main, InsetsProviderImpl())

        if (supportFragmentManager.fragments.isEmpty()) {
            supportFragmentManager.beginTransaction()
                .add(R.id.fragment_container, MainFragment())
                .commit()
        }
    }

    private fun setFragment(fragment: Fragment) {
        supportFragmentManager.run {
            val current = fragments.findLast { it.isVisible }
            beginTransaction()
                .setCustomAnimations(
                    R.animator.fragment_enter,
                    R.animator.fragment_exit,
                    R.animator.fragment_pop_enter,
                    R.animator.fragment_pop_exit,
                )
                .addToBackStack(fragment.javaClass.name)
                .apply { hide(current ?: return@apply) }
                .add(R.id.fragment_container, fragment)
                .commit()
        }
    }

    fun showPreference() = setFragment(PreferenceFragment())

    fun showSnapshots() = setFragment(SnapshotListFragment())

    fun showSnapshot(name: String) = setFragment(SnapshotFragment(name))
}