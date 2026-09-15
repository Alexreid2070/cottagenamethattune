package com.isrbet.cottagegames

import android.content.Context
import android.os.Bundle
import android.view.*
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import com.google.android.gms.common.SignInButton
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.totalBytesToDownload
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.Firebase
import com.isrbet.cottagegames.databinding.FragmentSignInBinding
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.security.MessageDigest
import java.util.UUID

const val DAYS_FOR_FLEXIBLE_UPDATE = 7
const val cMyRequestCode = 293847

class SignInFragment : Fragment() {
    private var _binding: FragmentSignInBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private lateinit var appUpdateManager: AppUpdateManager
    private lateinit var updateListener: InstallStateUpdatedListener

    private var bytesToDownload: Long = 0
    private var bytesDownloaded: Long = 0
    private var viewModel: LoginViewModel = LoginViewModel()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSignInBinding.inflate(inflater, container, false)

        binding.signInButton.setOnClickListener {
            viewModel.handleGoogleSignIn(requireContext())
        }
        viewModel.setSuccessCallback { signIn() }
        auth = Firebase.auth

        inflater.inflate(R.layout.fragment_sign_in, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        signIn()
    }

    private fun signIn() {
        val account = auth.currentUser
        MyApplication.userEmail = account?.email.toString()
        MyApplication.adminMode = MyApplication.userEmail == "alexreid2070@gmail.com"
        Timber.tag("Alex").d("Account is $account, adminMode is ${MyApplication.adminMode}")
        if (account == null) {
            (activity as MainActivity).setIsLoggedIn(false)
            binding.signInButton.visibility = View.VISIBLE
            binding.signInButton.setSize(SignInButton.SIZE_WIDE)
        } else {
            MyApplication.userPhotoURL = account.photoUrl.toString()

            binding.signInButton.visibility = View.GONE
            MyApplication.versionName = requireContext().packageManager.getPackageInfo(requireContext().packageName,0).versionName.toString()
            MyApplication.versionCode = requireContext().packageManager.getPackageInfo(requireContext().packageName,0).longVersionCode.toString()
            MyApplication.userName = account.displayName ?: ""
            MyApplication.userEmail = account.email ?: ""
            MyApplication.userPhotoURL = account.photoUrl.toString()
//            MyApplication.userAccount = account.account
            (activity as MainActivity).setIsLoggedIn(true)

            checkForAppUpdate()
            findNavController().navigate(R.id.navigation_home)
        }
    }

    private fun checkForAppUpdate() {
        appUpdateManager = AppUpdateManagerFactory.create(requireContext())

        // Returns an intent object that you use to check for an update.
        val appUpdateInfoTask = appUpdateManager.appUpdateInfo

        // Checks that the platform will allow the specified type of update.
        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                if (appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) ||
                    (appUpdateInfo.clientVersionStalenessDays() ?: -1) >= DAYS_FOR_FLEXIBLE_UPDATE
                    && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
                ) {
                    Timber.tag("Alex").d("update available")
                    binding.signInButton.visibility = View.GONE
                    binding.upgradeLayout.visibility = View.VISIBLE

                    updateListener = InstallStateUpdatedListener { state ->
                        // (Optional) Provide a download progress bar.
                        if (state.installStatus() == InstallStatus.DOWNLOADING) {
                            bytesToDownload = state.totalBytesToDownload
                            bytesDownloaded = state.bytesDownloaded()
                            updateProgressBar()
                        } else if (state.installStatus() == InstallStatus.DOWNLOADED) {
                            // Show a notification and request user confirmation to restart the app.
                            appUpdateManager.unregisterListener(updateListener)
                            enableRestartButton()
                        }
                    }
                    appUpdateManager.registerListener(updateListener)

                    // Request the update.
                    appUpdateManager.startUpdateFlowForResult(
                        appUpdateInfo,
                        // Or 'AppUpdateType.FLEXIBLE' for flexible updates.
                        AppUpdateType.FLEXIBLE,
                        ::startIntentSenderForResult,
                        cMyRequestCode
                    )
                }
            } else {
                Timber.tag("Alex").d("In else, no update available")
                findNavController().navigate(R.id.navigation_home)
            }
        }
    }

    private fun updateProgressBar() {
        binding.circularProgressBar.setProgressCompat(bytesDownloaded.toInt(), true)
        binding.circularProgressBar.max = bytesToDownload.toInt()
        val pct = ((bytesDownloaded * 1.0 / bytesToDownload) * 100.0).toInt()
        val mb = bytesToDownload / 1000000.0
        binding.downloadText.text = String.format("%d%% of %.2f MB", pct, mb)
    }
    private fun enableRestartButton() {
        binding.downloadText.text = getString(R.string.download_complete)
        binding.restartButton.setOnClickListener {
            appUpdateManager.completeUpdate()
        }
        binding.restartButton.isEnabled = true
    }

    override fun onResume() {
        super.onResume()
        if (this::appUpdateManager.isInitialized) {
            appUpdateManager
                .appUpdateInfo
                .addOnSuccessListener { appUpdateInfo ->
                    if (appUpdateInfo.updateAvailability()
                        == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
                    ) {
                        // If an in-app update is already running, resume the update.
                        appUpdateManager.startUpdateFlowForResult(
                            appUpdateInfo,
                            AppUpdateType.IMMEDIATE,
                            ::startIntentSenderForResult,
                            cMyRequestCode
                        )
                    }
                    // If the update is downloaded but not installed, notify the user to complete the update.
                    else if (appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED) {
                        completeUpdate()
                    }
                }
        }
    }

    private fun completeUpdate() {
        bytesDownloaded = bytesToDownload
        binding.upgradeLayout.visibility = View.VISIBLE
        enableRestartButton()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (this::appUpdateManager.isInitialized && this::updateListener.isInitialized) {
            appUpdateManager.unregisterListener(updateListener)
        }
        _binding = null
    }
}

class LoginViewModel : ViewModel() {
    private val serverClientId = "159385437888-48dj2c5r126c1k0freo9ss9nj7i5nlr0.apps.googleusercontent.com"
    private lateinit var successCallback: () -> Unit

    fun setSuccessCallback(iCallback: () -> Unit) {
        successCallback = iCallback
    }

    fun handleGoogleSignIn(context: Context) {

        viewModelScope.launch {
            googleSignIn(context).collect { result ->
                result.fold(
                    onSuccess = {
                        Timber.tag("Alex").d("Success in ViewModel")
                        successCallback()
                        // Handle success
                    },
                    onFailure = { e ->
                        Timber.tag("Alex").d("Failure in ViewModel")
                        // Handle error
                    }
                )
            }
        }
    }

    private fun googleSignIn(context: Context): Flow<Result<AuthResult>> {
        Timber.tag("Alex").d("in googleSignIn")

        val firebaseAuth = FirebaseAuth.getInstance()
        return callbackFlow {
            try {
                Timber.tag("Alex").d("in callbackflow try")
                // Initialize Credential Manager
                val credentialManager: CredentialManager = CredentialManager.create(context)

                // Generate a nonce (a random number used once)
                val ranNonce: String = UUID.randomUUID().toString()
                val bytes: ByteArray = ranNonce.toByteArray()
                val md: MessageDigest = MessageDigest.getInstance("SHA-256")
                val digest: ByteArray = md.digest(bytes)
                val hashedNonce: String = digest.fold("") { str, it -> str + "%02x".format(it) }

                // Set up Google ID option
                val googleIdOption: GetGoogleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setAutoSelectEnabled(true)
                    .setServerClientId(serverClientId)
                    .setNonce(hashedNonce)
                    .build()

                // Request credentials
                val request: GetCredentialRequest = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                // Get the credential result
                val result = credentialManager.getCredential(context, request)
                val credential = result.credential

                Timber.tag("Alex").d("Got here 1")
                // Check if the received credential is a valid Google ID Token
                if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    Timber.tag("Alex").d("Got here 2")
                    val googleIdTokenCredential =
                        GoogleIdTokenCredential.createFrom(credential.data)
                    MyApplication.userName = googleIdTokenCredential.givenName.toString()
                    Timber.tag("Alex").d("Set userGivenName to ${MyApplication.userName} ")
                    val authCredential =
                        GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                    val authResult = firebaseAuth.signInWithCredential(authCredential).await()

                    Timber.tag("Alex").d("the great unknown")
                    trySend(Result.success(authResult))
                } else {
                    throw RuntimeException("Received an invalid credential type")
                }
            } catch (e: GetCredentialCancellationException) {
                trySend(Result.failure(Exception("Sign-in was canceled. Please try again.")))

            } catch (e: Exception) {
                trySend(Result.failure(e))
            }
            awaitClose { }
        }
    }
}