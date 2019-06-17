package com.koalatea.sedaily

import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.onNavDestinationSelected
import androidx.navigation.ui.setupWithNavController
import com.koalatea.sedaily.database.model.Episode
import com.koalatea.sedaily.feature.auth.UserRepository
import com.koalatea.sedaily.feature.player.AudioService
import com.koalatea.sedaily.feature.player.PlayerCallback
import com.koalatea.sedaily.feature.player.PlayerFragment
import com.koalatea.sedaily.feature.player.PlayerStatus
import com.koalatea.sedaily.util.AbsentLiveData
import com.koalatea.sedaily.util.isServiceRunning
import com.koalatea.sedaily.util.setupActionBar
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.include_default_toolbar.*
import org.koin.android.ext.android.inject

class MainActivity : AppCompatActivity(), PlayerCallback {

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as AudioService.AudioServiceBinder

            binder.service.episodeId?.let { episodeId ->
                addPlayerFragment(episodeId, false)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
        }
    }

    // FIXME :: Remove
    private val userRepository: UserRepository by inject()

    private var playerFragment: PlayerFragment? = null
    private var isAudioServiceBound: Boolean = false

    override val playerStatusLiveData: LiveData<PlayerStatus>
        get() = playerFragment?.playerStatusLiveData ?: AbsentLiveData.create()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        val navController = mainNavHostFragment.findNavController()
        // Define top level screens.
        // FIXME :: Use correct top-level tabs, setOf(R.id.navigation_home, R.id.navigation_saved, R.id.navigation_profile)
        val appBarConfiguration = AppBarConfiguration(setOf(R.id.navigation_home, R.id.navigation_auth, R.id.navigation_auth))

        navController.setupActionBar(this, appBarConfiguration)
        setupBottomNavMenu(navController)

        handleIntent(intent)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        // If audio service is running, add the player fragment
        if (applicationContext.isServiceRunning(AudioService::class.java.name)) {
            AudioService.newIntent(this).also { intent ->
                bindService(intent, connection, Context.BIND_AUTO_CREATE)

                isAudioServiceBound = true
            }
        }
    }

    override fun onStop() {
        super.onStop()

        if (isAudioServiceBound) {
            unbindService(connection)

            isAudioServiceBound = false
        }
    }

    private fun setupBottomNavMenu(navController: NavController) = bottomNavigationView?.setupWithNavController(navController)

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.menu_main, menu)
        userRepository.token?.let { token ->
            if (token.isNotBlank()) {
                setLogout(menu)
            }
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.search -> {
                onSearchRequested()
                true
            }
            R.id.home -> {
                mainNavHostFragment.findNavController().navigate(R.id.navigation_home)
                true
            }
            else -> item.onNavDestinationSelected(mainNavHostFragment.findNavController()) || super.onOptionsItemSelected(item)
        }
    }

    override fun isPLaying(episodeId: String): Boolean? = playerFragment?.isPLaying(episodeId)

    override fun play(episode: Episode) {
        playerFragment?.play(episode) ?: addPlayerFragment(episode._id, true)
    }

    override fun stop() {
        playerFragment?.let {
            playerFragment?.stop()

            supportFragmentManager.beginTransaction().remove(it)
            playerFragment = null
        }
    }

    private fun handleIntent(intent: Intent) {
        if (Intent.ACTION_SEARCH == intent.action) {
            intent.getStringExtra(SearchManager.QUERY)?.also { query ->
                searchPodcasts(query)
            }
        }
    }

    private fun addPlayerFragment(episodeId: String, autoPLay: Boolean) {
        playerFragment = PlayerFragment.newInstance(episodeId, autoPLay).also {
            supportFragmentManager.beginTransaction().replace(R.id.fragmentContainer, it).commit()
        }
    }

    private fun searchPodcasts(query: String) {

    }

    override fun onSupportNavigateUp(): Boolean {
        return mainNavHostFragment.findNavController().navigateUp()
    }

    private fun setLogout(menu: Menu) {
        val authItem = menu.findItem(R.id.navigation_auth)
        authItem?.title = "Logout"
        authItem.setOnMenuItemClickListener {
            userRepository.token = ""
            val intent = Intent(this@MainActivity, MainActivity::class.java)
            startActivity(intent)
            false
        }
    }
}
