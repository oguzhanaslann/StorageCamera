package com.oguzhanaslann.storagecamera

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.oguzhanaslann.storagecamera.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.*

class MainActivity : AppCompatActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    private lateinit var binding: ActivityMainBinding

    private lateinit var permissionsLauncher: ActivityResultLauncher<Array<String>>

    private val pagerAdapter = object : FragmentStateAdapter(supportFragmentManager, lifecycle) {
        override fun getItemCount(): Int {
            return 2
        }

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> PhotoFragment.newInstance(StorageType.Internal)
                1 -> PhotoFragment.newInstance(StorageType.Scoped)
                else -> PhotoFragment.newInstance(StorageType.Internal)
            }
        }

    }

    private val takePicturePreview =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) {
            when (binding.content.viewPager.currentItem) {
                0 -> saveBitmapToLocalStorage(it)
                1 -> saveBitmapToScopedStorage(it)
            }
        }

    private fun saveBitmapToLocalStorage(photo: Bitmap?) {
        lifecycleScope.launchWhenStarted {
            withContext(Dispatchers.IO) {
                photo?.let {
                    openFileOutput(
                        "image_${UUID.randomUUID().toString().take(5)}.jpg",
                        MODE_PRIVATE
                    ).use {
                        photo.compress(Bitmap.CompressFormat.JPEG, 100, it)
                    }
                }
            }
            notifyInternalStorageListeners()
        }
    }

    private fun notifyInternalStorageListeners() {
        mainViewModel.sendEvent(MainViewModel.StorageEvent.InternalStorageUpdated)
    }

    private fun saveBitmapToScopedStorage(bitmap: Bitmap?) {
        bitmap?.let {
            savePhotoToExternalStorage(
                "image_${UUID.randomUUID().toString().take(5)}",
                it
            )
            notifyScopedStorageListeners()
        }
    }

    private fun notifyScopedStorageListeners() {
        mainViewModel.sendEvent(MainViewModel.StorageEvent.ScopeStorageUpdated)
    }

    private fun savePhotoToExternalStorage(displayName: String, bmp: Bitmap): Boolean {
        val imageCollection = sdk29AndUp {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } ?: MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.WIDTH, bmp.width)
            put(MediaStore.Images.Media.HEIGHT, bmp.height)
        }
        return try {
            contentResolver.insert(imageCollection, contentValues)?.also { uri ->
                contentResolver.openOutputStream(uri).use { outputStream ->
                    if (!bmp.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)) {
                        throw IOException("Couldn't save bitmap")
                    }
                }
            } ?: throw IOException("Couldn't create MediaStore entry")
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    private var actionMode: ActionMode? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        binding.fab.setOnClickListener { view ->
            takePicturePreview.launch(null)
        }

        binding.fabPermission.setOnClickListener {
            requestPermissionsIfNeeded()
        }


        binding.content.viewPager.adapter = pagerAdapter

        TabLayoutMediator(
            binding.content.tabLayout,
            binding.content.viewPager
        ) { tab, position ->
            tab.text = when (position) {
                0 -> "Internal"
                1 -> "Scoped"
                else -> "Unknown"
            }

        }.attach()


        permissionsLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                updatePermissionsStatus()
            }


        subscribeObservers()
    }

    val callback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            binding.toolbar.isHidden = true
            menuInflater.inflate(R.menu.contextual_action_bar, menu)
            mode?.title = "Selected ${binding.content.viewPager.currentItem}"

            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            return false
        }

        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
            return when (item?.itemId) {
                R.id.action_delete -> {
                    mainViewModel.deleteSelectedPhotos()
                    true
                }
                else -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode?) {
            binding.toolbar.isHidden = false
            mainViewModel.deactivateDeleteMode()
        }
    }

    private fun subscribeObservers() {
        lifecycleScope.launch {
            mainViewModel.shouldRequestPermission
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collect {
                    binding.fabPermission.isVisible = it
                }
        }


        lifecycleScope.launch {
            mainViewModel.deleteMode
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collect {
                    binding.fab.isVisible = !it
                    binding.fabPermission.isVisible = !it
                    if (it) {
                        actionMode = startSupportActionMode(callback)
                    } else {
                        actionMode?.finish()
                        actionMode = null
                    }
                }
        }

        lifecycleScope.launch {
            mainViewModel.selectedPhotos
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collect {
                    if (it.isEmpty()) {
                        actionMode?.finish()
                        actionMode = null
                    } else {
                        actionMode?.title = "Selected ${it.size}"
                    }
                }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.e("TAG", "onResume: ")
        updatePermissionsStatus()
        mainViewModel.sendEvent(MainViewModel.StorageEvent.InternalStorageUpdated)
        mainViewModel.sendEvent(MainViewModel.StorageEvent.ScopeStorageUpdated)
    }


    private fun updatePermissionsStatus() {
        val hasReadPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        val hasWritePermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

        val minSdk29 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        mainViewModel.setPermissionsGranted(hasReadPermission, hasWritePermission || minSdk29)
    }

    private fun requestPermissionsIfNeeded() {
        val hasReadPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        val hasWritePermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

        val minSdk29 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        val readPermissionGranted = hasReadPermission
        val writePermissionGranted = hasWritePermission || minSdk29

        val permissionsToRequest = mutableListOf<String>()
        if (!writePermissionGranted) {
            permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (!readPermissionGranted) {
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (permissionsToRequest.isNotEmpty()) {
            permissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

fun sdk29AndUp(block: () -> Uri): Uri? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        block()
    } else {
        null
    }
}

var View.isHidden: Boolean
    get() = visibility == View.INVISIBLE
    set(value) {
        this.visibility = if (value) View.INVISIBLE else View.VISIBLE
    }
