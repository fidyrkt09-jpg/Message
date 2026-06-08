package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.local.MessageEntity
import com.example.data.local.UserEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatApp(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val users by viewModel.usersList.collectAsState()
    val messages by viewModel.currentConversationMessages.collectAsState()
    val selectedUser by viewModel.selectedUser.collectAsState()
    val messageInput by viewModel.messageInput.collectAsState()
    val editingProfile by viewModel.editingProfile.collectAsState()
    val usernameDraft by viewModel.usernameDraft.collectAsState()
    val myUsername by viewModel.myUsernameState.collectAsState()

    var showSecurityVerificationDialog by remember { mutableStateOf<UserEntity?>(null) }

    // Dialog for active profile editing
    if (editingProfile) {
        AlertDialog(
            onDismissRequest = { viewModel.closeProfileEditor() },
            title = { Text("Modifier mon Profil") },
            text = {
                Column {
                    Text("Choisissez un nom d'utilisateur public visible en temps réel :", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = usernameDraft,
                        onValueChange = { viewModel.updateUsernameDraft(it) },
                        label = { Text("Nom d'utilisateur") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("username_input_field"),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { viewModel.saveProfile() })
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.saveProfile() },
                    modifier = Modifier.testTag("save_profile_button")
                ) {
                    Text("Sauvegarder")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.closeProfileEditor() }) {
                    Text("Annuler")
                }
            }
        )
    }

    // Cryptographic Security Verification Dialog (Signal-style key verification)
    showSecurityVerificationDialog?.let { user ->
        Dialog(onDismissRequest = { showSecurityVerificationDialog = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Sécurité",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Vérification Cryptographique",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Cet échange est protégé de bout en bout (E2EE) par un chiffrement asymétrique RSA-2048 et symétrique AES-256.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Alice fingerprints
                    Text(
                        text = "VOTRE EMPREINTE :",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = viewModel.myKeyFingerprint,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Bob fingerprint
                    Text(
                        text = "EMPREINTE DE ${user.username.uppercase()} :",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = user.publicKeyFingerprint,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = "Verified",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Canal cryptographique authentifié",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4CAF50)
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { showSecurityVerificationDialog = null },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Fermer")
                    }
                }
            }
        }
    }

    // Adaptive Responsive Layout
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isTablet = maxWidth >= 600.dp

        if (isTablet) {
            // Side-by-Side Split View
            Row(modifier = Modifier.fillMaxSize()) {
                // Sidebar Directory list (Width 320dp)
                UserListPane(
                    myId = viewModel.myId,
                    myUsername = myUsername,
                    myFingerprint = viewModel.myKeyFingerprint,
                    users = users,
                    selectedUser = selectedUser,
                    onUserSelected = { viewModel.selectUser(it) },
                    onEditProfile = { viewModel.openProfileEditor() },
                    onRefresh = { viewModel.manualRefreshDiscovery() },
                    modifier = Modifier
                        .width(340.dp)
                        .fillMaxHeight()
                )

                // Divider
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Right Conversational Details Pane
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    if (selectedUser != null) {
                        ActiveChatPane(
                            recipient = selectedUser!!,
                            messages = messages,
                            input = messageInput,
                            onInputChange = { viewModel.updateMessageInput(it) },
                            onSendMessage = { viewModel.sendSecureMessage() },
                            onBack = { viewModel.selectUser(null) },
                            onShowSecurityKeys = { showSecurityVerificationDialog = it },
                            onClearHistory = { viewModel.clearHistory(it.id) },
                            isTablet = true
                        )
                    } else {
                        // Grand E2EE Dashboard visual placeholder when no chat selected
                        EmptyChatStatePlaceholder()
                    }
                }
            }
        } else {
            // Single-Screen Handheld Layout: Switching animation
            AnimatedContent(
                targetState = selectedUser,
                transitionSpec = {
                    if (targetState != null) {
                        slideInHorizontally { width -> width }.togetherWith(slideOutHorizontally { width -> -width })
                    } else {
                        slideInHorizontally { width -> -width }.togetherWith(slideOutHorizontally { width -> width })
                    }
                },
                label = "ScreenNavigation"
            ) { targetUser ->
                if (targetUser != null) {
                    ActiveChatPane(
                        recipient = targetUser,
                        messages = messages,
                        input = messageInput,
                        onInputChange = { viewModel.updateMessageInput(it) },
                        onSendMessage = { viewModel.sendSecureMessage() },
                        onBack = { viewModel.selectUser(null) },
                        onShowSecurityKeys = { showSecurityVerificationDialog = it },
                        onClearHistory = { viewModel.clearHistory(it.id) },
                        isTablet = false
                    )
                } else {
                    UserListPane(
                        myId = viewModel.myId,
                        myUsername = myUsername,
                        myFingerprint = viewModel.myKeyFingerprint,
                        users = users,
                        selectedUser = null,
                        onUserSelected = { viewModel.selectUser(it) },
                        onEditProfile = { viewModel.openProfileEditor() },
                        onRefresh = { viewModel.manualRefreshDiscovery() },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserListPane(
    myId: String,
    myUsername: String,
    myFingerprint: String,
    users: List<UserEntity>,
    selectedUser: UserEntity?,
    onUserSelected: (UserEntity) -> Unit,
    onEditProfile: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            "Messagerie Sécurisée",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            PulsingOnlineDot(modifier = Modifier.size(8.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Lobby de synchronisation actif",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.testTag("refresh_discovery_button")
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Actualiser")
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // Own local identity card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "User",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = myUsername,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Fingerprint: ${myFingerprint.take(20)}...",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onEditProfile,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("edit_profile_icon_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Modifier identité",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Text(
                text = "APPAREILS CONNECTÉS (${users.count { it.isOnline }})",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp)
            )

            if (users.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Recherche d'autres terminaux...",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "L'application diffuse un signal d'existence sécurisé RSA toutes les 8s. Tout autre terminal démarrant l'application apparaîtra ici instantanément.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(users, key = { it.id }) { user ->
                        val isSelected = selectedUser?.id == user.id
                        val itemColor = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        } else {
                            Color.Transparent
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(itemColor)
                                .clickable { onUserSelected(user) }
                                .padding(horizontal = 20.dp, vertical = 14.dp)
                                .testTag("user_item_${user.id}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Avatar with online signal overlay
                            Box(modifier = Modifier.size(46.dp)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            if (user.isOnline) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                            else MaterialTheme.colorScheme.surfaceVariant,
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val userLetter = user.username.firstOrNull()?.toString()?.uppercase() ?: "?"
                                    Text(
                                        text = userLetter,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (user.isOnline) MaterialTheme.colorScheme.primary else Color.Gray
                                    )
                                }
                                
                                // Green online pulsing badge
                                if (user.isOnline) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .background(Color.White, CircleShape)
                                            .align(Alignment.BottomEnd)
                                            .padding(1.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color(0xFF4CAF50), CircleShape)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = user.username,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (user.isOnline) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "Online",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF4CAF50),
                                            fontWeight = FontWeight.Bold
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "Inactif",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.Gray
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Keys: ${user.publicKeyFingerprint.take(16)}...",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveChatPane(
    recipient: UserEntity,
    messages: List<MessageEntity>,
    input: String,
    onInputChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onBack: () -> Unit,
    onShowSecurityKeys: (UserEntity) -> Unit,
    onClearHistory: (UserEntity) -> Unit,
    isTablet: Boolean
) {
    val listState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current

    // Automatically scroll to the bottom when new E2EE statements are delivered
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.clickable { onShowSecurityKeys(recipient) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = recipient.username,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Lock",
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Chiffrement Actif (E2EE)",
                                    fontSize = 11.sp,
                                    color = Color(0xFF4CAF50),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    if (!isTablet) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("chat_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onShowSecurityKeys(recipient) },
                        modifier = Modifier.testTag("show_security_dialog_button")
                    ) {
                        Icon(imageVector = Icons.Default.Security, contentDescription = "Cryptographie")
                    }
                    IconButton(
                        onClick = { onClearHistory(recipient) },
                        modifier = Modifier.testTag("clear_history_button")
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Effacer l'historique")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // Conversation messages area
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "Secure lock",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Début du canal chiffré de bout en bout",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Les messages envoyés dans ce salon sont chiffrés avec la clé publique et signés avec votre clé privée. Ni le serveur de routage ni aucun tiers ne peut lire ces échanges.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages, key = { it.id }) { message ->
                        MessageBubble(message = message)
                    }
                }
            }

            // Chat input control panel
            Surface(
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = onInputChange,
                        placeholder = { Text("Écrire un message chiffré...") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_input_field"),
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (input.isNotBlank()) {
                                onSendMessage()
                                keyboardController?.hide()
                            }
                        })
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FloatingActionButton(
                        onClick = {
                            if (input.isNotBlank()) {
                                onSendMessage()
                                keyboardController?.hide()
                            }
                        },
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("send_msg_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: MessageEntity) {
    val formatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeString = remember(message.timestamp) { formatter.format(Date(message.timestamp)) }

    val alignment = if (message.isSentByMe) Alignment.End else Alignment.Start
    val bubbleColor = if (message.isSentByMe) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (message.isSentByMe) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val bubbleShape = if (message.isSentByMe) {
        RoundedCornerShape(bottomStart = 16.dp, topStart = 16.dp, topEnd = 16.dp, bottomEnd = 2.dp)
    } else {
        RoundedCornerShape(bottomStart = 2.dp, topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("msg_bubble_${message.id}"),
        horizontalAlignment = alignment
    ) {
        // Encrypted Bubble Frame
        Surface(
            color = bubbleColor,
            shape = bubbleShape,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) {
                Text(
                    text = message.body,
                    color = textColor,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = timeString,
                        fontSize = 10.sp,
                        color = textColor.copy(alpha = 0.7f)
                    )
                    if (message.isSentByMe) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Lock",
                            tint = textColor.copy(alpha = 0.7f),
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
            }
        }

        // Lock & Signature Status Badges
        Row(
            modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (message.decryptionSuccess) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Signature Valide",
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(10.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "Chiffrement vérifié RSA+AES",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF4CAF50).copy(alpha = 0.8f)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Signature Erreur",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(10.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "Alerte de chiffrement",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun EmptyChatStatePlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 420.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = "E2EE",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                modifier = Modifier.size(100.dp)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                "Chiffrement de bout en bout",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "Sélectionnez un appareil actif dans la liste de gauche pour initier une conversation cryptographique hautement confidentielle.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(20.dp))
            
            // Cryptographic steps explanations
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CryptoStepRow(
                    stepNum = "1",
                    title = "Échange des clés de bout en bout",
                    desc = "Les deux appareils échangent de manière transparente leurs clés publiques RSA lors de signaux de présence réguliers."
                )
                CryptoStepRow(
                    stepNum = "2",
                    title = "Clé AES éphémère unilatérale",
                    desc = "Chaque message génère sa propre clé AES-256 symétrique éphémère, empêchant toute lecture rétroactive ou par interception."
                )
                CryptoStepRow(
                    stepNum = "3",
                    title = "Signature numérique asymétrique",
                    desc = "La clé privée de l'expéditeur signe numériquement le message pour se prémunir contre toute usurpation ou modification d'identité."
                )
            }
        }
    }
}

@Composable
fun CryptoStepRow(stepNum: String, title: String, desc: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stepNum,
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Start)
            Spacer(modifier = Modifier.height(2.dp))
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Start)
        }
    }
}

@Composable
fun PulsingOnlineDot(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF4CAF50)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "PulsingDot")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Scale"
    )
    val opacity by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Opacity"
    )

    Box(
        modifier = modifier
            .background(color.copy(alpha = opacity), CircleShape)
            .padding(2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .scale(scale)
                .background(color, CircleShape)
        )
    }
}
