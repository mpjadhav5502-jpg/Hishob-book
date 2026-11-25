package com.example.ui

import android.app.Application
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.auth.attemptAutoSignIn
import com.example.auth.signOutUser
import com.example.data.export.ReportExporter
import com.example.data.ocr.BillOcrService
import com.example.data.repository.BillImageLocalStore
import com.example.data.repository.HisabRepository
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.BillEditorScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ProductsScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.theme.LossRed
import com.example.ui.theme.ProfitGreen
import com.example.ui.viewmodel.HisabViewModel
import com.example.ui.viewmodel.UiState
import com.example.util.observeNetworkConnectivity
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

private enum class TopTab(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    LEDGER("ledger", R.string.nav_ledger, Icons.AutoMirrored.Filled.ReceiptLong, Icons.AutoMirrored.Outlined.ReceiptLong),
    NEW_PAPER("new_paper", R.string.nav_new_paper, Icons.AutoMirrored.Filled.NoteAdd, Icons.AutoMirrored.Outlined.NoteAdd),
    REPORTS("reports", R.string.nav_reports, Icons.Filled.Assessment, Icons.Outlined.Assessment),
    PRODUCTS("products", R.string.nav_products, Icons.Filled.Inventory2, Icons.Outlined.Inventory2)
}

@Composable
fun HisabApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    var isCheckingAutoSignIn by remember { mutableStateOf(Firebase.auth.currentUser == null) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    LaunchedEffect(Unit) {
        if (Firebase.auth.currentUser == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {
                    currentUser = Firebase.auth.currentUser
                    isCheckingAutoSignIn = false
                },
                onUnauthenticated = {
                    isCheckingAutoSignIn = false
                },
                scope = scope
            )
        } else {
            isCheckingAutoSignIn = false
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            isCheckingAutoSignIn = isCheckingAutoSignIn,
            onAuthSuccess = { currentUser = Firebase.auth.currentUser }
        )
    } else {
        key(user.uid) {
            AuthenticatedWorkspace(
                user = user,
                onSignOut = {
                    signOutUser(credentialManager, scope) {
                        currentUser = null
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedWorkspace(
    user: FirebaseUser,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: HisabViewModel = viewModel(
        key = user.uid,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val dbId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(dbId)
                val imageStore = BillImageLocalStore(app)
                val repo = HisabRepository(db = db, auth = Firebase.auth, localImageStore = imageStore)
                HisabViewModel(
                    repository = repo,
                    imageStore = imageStore,
                    ocrService = BillOcrService(),
                    exporter = ReportExporter(),
                    currentUserId = user.uid
                )
            }
        }
    )

    val billsState by viewModel.billsState.collectAsStateWithLifecycle()
    val productsState by viewModel.productsState.collectAsStateWithLifecycle()
    val todaySummary by viewModel.todaySummary.collectAsStateWithLifecycle()
    val reportFilter by viewModel.reportFilter.collectAsStateWithLifecycle()
    val reportSummary by viewModel.reportSummary.collectAsStateWithLifecycle()
    val editorState by viewModel.editorState.collectAsStateWithLifecycle()
    val bannerMessage by viewModel.bannerMessage.collectAsStateWithLifecycle()
    val isDeletingBill by viewModel.isDeletingBill.collectAsStateWithLifecycle()
    val isOnline by remember { observeNetworkConnectivity(context) }.collectAsStateWithLifecycle(initialValue = true)

    var selectedTab by remember { mutableStateOf(TopTab.LEDGER) }
    var isEditingBill by remember { mutableStateOf(false) }
    var showAccountDialog by remember { mutableStateOf(false) }
    var pendingPhotoRunsOcr by remember { mutableStateOf(true) }
    var pendingPhotoIsNewScan by remember { mutableStateOf(true) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(bannerMessage) {
        val msg = bannerMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg.second)
            viewModel.clearBanner()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            if (pendingPhotoIsNewScan) {
                viewModel.startPhotoScanFlow(uri)
                isEditingBill = true
            } else {
                viewModel.replacePhotoOnCurrentBill(uri, runOcrAgain = pendingPhotoRunsOcr)
            }
        }
    }

    val createPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) viewModel.exportPdfToUri(context, uri)
    }

    val createCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) viewModel.exportCsvToUri(context, uri)
    }

    fun launchShare(isPdf: Boolean) {
        viewModel.createShareFile(context, isPdf) { file, mime ->
            val shareUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, shareUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share HisabPro Report"))
        }
    }

    val catalogList = (productsState as? UiState.Success)?.data ?: emptyList()

    if (isEditingBill) {
        Scaffold(
            contentWindowInsets = WindowInsets.navigationBars,
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                BillEditorScreen(
                    editorState = editorState,
                    catalogProducts = catalogList,
                    onBack = {
                        isEditingBill = false
                        selectedTab = TopTab.LEDGER
                    },
                    onTitleChange = viewModel::updateBillTitle,
                    onVendorChange = viewModel::updateVendorOrParty,
                    onDateChange = viewModel::updateBillDate,
                    onNotesChange = viewModel::updateNotes,
                    onPickPhoto = { runOcr ->
                        pendingPhotoIsNewScan = false
                        pendingPhotoRunsOcr = runOcr
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onRetryOcr = viewModel::retryOcrOnCurrentPhoto,
                    onRemovePhoto = viewModel::removePhotoFromEditor,
                    onAddQuickPreset = viewModel::addQuickPreset,
                    onAddCatalogProduct = viewModel::addLineItem,
                    onAddBlankRow = { viewModel.addLineItem(null) },
                    onUpdateLine = viewModel::updateLineItem,
                    onRemoveLine = viewModel::removeLineItem,
                    onSave = {
                        viewModel.saveEditorBill {
                            isEditingBill = false
                            selectedTab = TopTab.LEDGER
                        }
                    }
                )
            }
        }
    } else {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavRail = maxWidth >= 600.dp

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("HisabPro", style = MaterialTheme.typography.titleLarge)
                                Icon(
                                    imageVector = if (isOnline) Icons.Filled.CloudDone else Icons.Filled.CloudOff,
                                    contentDescription = if (isOnline) "Online synced" else "Offline",
                                    tint = if (isOnline) ProfitGreen else LossRed,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { showAccountDialog = true },
                                modifier = Modifier.testTag("account_button")
                            ) {
                                Icon(Icons.Filled.AccountCircle, contentDescription = "Account and sign out")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                },
                bottomBar = {
                    if (!useNavRail) {
                        NavigationBar(windowInsets = WindowInsets.navigationBars) {
                            TopTab.entries.forEach { tab ->
                                val selected = selectedTab == tab
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        if (tab == TopTab.NEW_PAPER) {
                                            viewModel.startBlankNewPaper()
                                            isEditingBill = true
                                        } else {
                                            selectedTab = tab
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = stringResource(tab.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(tab.labelRes)) },
                                    modifier = Modifier.testTag("nav_${tab.route}")
                                )
                            }
                        }
                    }
                },
                snackbarHost = {
                    SnackbarHost(snackbarHostState) { data ->
                        val isErr = bannerMessage?.first == true
                        Snackbar(
                            snackbarData = data,
                            containerColor = if (isErr) LossRed else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useNavRail) {
                        NavigationRail(modifier = Modifier.fillMaxHeight()) {
                            TopTab.entries.forEach { tab ->
                                val selected = selectedTab == tab
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = {
                                        if (tab == TopTab.NEW_PAPER) {
                                            viewModel.startBlankNewPaper()
                                            isEditingBill = true
                                        } else {
                                            selectedTab = tab
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = stringResource(tab.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(tab.labelRes)) }
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        when (selectedTab) {
                            TopTab.LEDGER, TopTab.NEW_PAPER -> DashboardScreen(
                                billsState = billsState,
                                todaySummary = todaySummary,
                                isOnline = isOnline,
                                isDeletingBill = isDeletingBill,
                                onScanBillPhoto = {
                                    pendingPhotoIsNewScan = true
                                    pendingPhotoRunsOcr = true
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                onNewPaper = {
                                    viewModel.startBlankNewPaper()
                                    isEditingBill = true
                                },
                                onEditBill = { bill ->
                                    viewModel.openBillForEdit(bill)
                                    isEditingBill = true
                                },
                                onDeleteBill = { bill -> viewModel.deleteBill(bill) }
                            )

                            TopTab.REPORTS -> ReportsScreen(
                                filterState = reportFilter,
                                summary = reportSummary,
                                onPeriodSelected = viewModel::setReportPeriod,
                                onShiftPeriod = viewModel::shiftReportPeriod,
                                onAnchorDateChanged = viewModel::setReportAnchorDate,
                                onCustomRangeChanged = viewModel::setCustomDateRange,
                                onDownloadPdf = {
                                    val slug = reportSummary.period.name.lowercase()
                                    createPdfLauncher.launch("HisabPro_${slug}_report.pdf")
                                },
                                onDownloadCsv = {
                                    val slug = reportSummary.period.name.lowercase()
                                    createCsvLauncher.launch("HisabPro_${slug}_report.csv")
                                },
                                onSharePdf = { launchShare(isPdf = true) },
                                onShareCsv = { launchShare(isPdf = false) }
                            )

                            TopTab.PRODUCTS -> ProductsScreen(
                                productsState = productsState,
                                onSaveProduct = viewModel::saveProduct,
                                onDeleteProduct = viewModel::deleteProduct
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAccountDialog) {
        AlertDialog(
            onDismissRequest = { showAccountDialog = false },
            title = { Text("Account & Sync") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = user.displayName ?: "Signed-in User",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = user.email.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (isOnline) "Cloud Status: Connected to Firestore" else "Cloud Status: Offline",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isOnline) ProfitGreen else LossRed
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAccountDialog = false
                        onSignOut()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LossRed),
                    modifier = Modifier.testTag("sign_out_button")
                ) {
                    Text("Sign Out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAccountDialog = false }) { Text("Close") }
            }
        )
    }
}
