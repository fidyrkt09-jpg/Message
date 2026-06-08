package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ChatDatabase
import com.example.data.local.MessageEntity
import com.example.data.local.UserEntity
import com.example.data.repository.ChatRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val chatDatabase = ChatDatabase.getDatabase(application)
    private val chatRepository = ChatRepository(application, chatDatabase.chatDao(), viewModelScope)

    // Current local profile identity info
    val myId: String = chatRepository.myId
    val myPublicKeyBase64: String = chatRepository.myPublicKeyBase64
    val myKeyFingerprint: String
        get() = if (myPublicKeyBase64.length > 20) {
            "SHA256:" + myPublicKeyBase64.hashCode().toString(16).uppercase()
        } else {
            "N/A"
        }

    // Live state flow of our local username
    private val _myUsernameState = MutableStateFlow(chatRepository.myUsername)
    val myUsernameState: StateFlow<String> = _myUsernameState.asStateFlow()

    // Selected user for active conversation
    private val _selectedUser = MutableStateFlow<UserEntity?>(null)
    val selectedUser: StateFlow<UserEntity?> = _selectedUser.asStateFlow()

    // Text field input message draft
    private val _messageInput = MutableStateFlow("")
    val messageInput: StateFlow<String> = _messageInput.asStateFlow()

    // Profile editing dialog state
    private val _editingProfile = MutableStateFlow(false)
    val editingProfile: StateFlow<Boolean> = _editingProfile.asStateFlow()

    // Temporary name for the profile editing text field
    private val _usernameDraft = MutableStateFlow("")
    val usernameDraft: StateFlow<String> = _usernameDraft.asStateFlow()

    // Global peer directory list
    val usersList: StateFlow<List<UserEntity>> = chatRepository.activeUsersFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Reactive list of messages for the currently selected chat conversation
    val currentConversationMessages: StateFlow<List<MessageEntity>> = _selectedUser
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(emptyList())
            } else {
                chatRepository.getConversationFlow(user.id)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun selectUser(user: UserEntity?) {
        _selectedUser.value = user
        // Reset typing draft when switching rooms
        _messageInput.value = ""
    }

    fun updateMessageInput(input: String) {
        _messageInput.value = input
    }

    fun openProfileEditor() {
        _usernameDraft.value = _myUsernameState.value
        _editingProfile.value = true
    }

    fun closeProfileEditor() {
        _editingProfile.value = false
    }

    fun saveProfile() {
        val newName = _usernameDraft.value.trim()
        if (newName.isNotEmpty()) {
            chatRepository.updateUsername(newName)
            _myUsernameState.value = chatRepository.myUsername
        }
        _editingProfile.value = false
    }

    fun updateUsernameDraft(draft: String) {
        _usernameDraft.value = draft
    }

    fun manualRefreshDiscovery() {
        chatRepository.broadcastPresence()
    }

    fun clearHistory(otherUserId: String) {
        viewModelScope.launch {
            chatRepository.clearChatHistory(otherUserId)
        }
    }

    fun sendSecureMessage() {
        val recipient = _selectedUser.value ?: return
        val rawText = _messageInput.value.trim()
        if (rawText.isEmpty()) return

        // Clear input instantly for snappy performance feel
        _messageInput.value = ""

        viewModelScope.launch {
            chatRepository.sendSecureMessage(
                recipientId = recipient.id,
                recipientUsername = recipient.username,
                recipientPublicKeyBase64 = recipient.publicKey,
                textMessage = rawText
            )
        }
    }
}
