package com.flxrs.dankchat.chat.user

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.service.ChatRepository
import com.flxrs.dankchat.service.DataRepository
import com.flxrs.dankchat.service.api.dto.HelixUserDto
import com.flxrs.dankchat.service.api.dto.UserFollowsDto
import com.flxrs.dankchat.utils.DateTimeUtils.asParsedZonedDateTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserPopupViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository,
    private val dataRepository: DataRepository
) : ViewModel() {

    private val args = UserPopupDialogFragmentArgs.fromSavedStateHandle(savedStateHandle)

    sealed class UserPopupState {
        object Loading : UserPopupState()
        data class Error(val throwable: Throwable? = null) : UserPopupState()
        data class Success(
            val userId: String,
            val userName: String,
            val displayName: String,
            val created: String,
            val avatarUrl: String,
            val isFollowing: Boolean = false,
            val followingSince: String? = null,
            val isBlocked: Boolean = false
        ) : UserPopupState()
    }

    private val _userPopupState = MutableStateFlow<UserPopupState>(UserPopupState.Loading)
    val userPopupState: StateFlow<UserPopupState> = _userPopupState.asStateFlow()

    val isFollowing: Boolean
        get() {
            val state = userPopupState.value
            return state is UserPopupState.Success && state.isFollowing
        }
    val isBlocked: Boolean
        get() {
            val state = userPopupState.value
            return state is UserPopupState.Success && state.isBlocked
        }

    val displayNameOrNull: String?
        get() = (userPopupState.value as? UserPopupState.Success)?.displayName

    init {
        loadData()
    }

    fun followUser() = updateStateWith { targetUserId, currentUserId, oAuth ->
        dataRepository.followUser(oAuth, currentUserId, targetUserId)
    }

    fun blockUser() = updateStateWith { targetUserId, _, oAuth ->
        dataRepository.blockUser(oAuth, targetUserId)
        chatRepository.addUserBlock(targetUserId)
    }

    fun unfollowUser() = updateStateWith { targetUserId, currentUserId, oAuth ->
        dataRepository.unfollowUser(oAuth, currentUserId, targetUserId)
    }

    fun unblockUser() = updateStateWith { targetUserId, _, oAuth ->
        dataRepository.unblockUser(oAuth, targetUserId)
        chatRepository.removeUserBlock(targetUserId)
    }

    private inline fun updateStateWith(crossinline block: suspend (String, String, String) -> Unit) = viewModelScope.launch {
        val result = runCatching { block(args.targetUserId, args.currentUserId, args.oAuth) }
        when {
            result.isFailure -> _userPopupState.value = UserPopupState.Error(result.exceptionOrNull())
            else             -> loadData()
        }
    }

    private fun loadData() = viewModelScope.launch {
        _userPopupState.value = UserPopupState.Loading

        val result = runCatching {
            val channelId = args.channel?.let { dataRepository.getUserIdByName(args.oAuth, it) }
            val channelUserFollows = channelId?.let { dataRepository.getUserFollows(args.oAuth, args.targetUserId, channelId) }
            val user = dataRepository.getUser(args.oAuth, args.targetUserId)
            val currentUserFollows = dataRepository.getUserFollows(args.oAuth, args.currentUserId, args.targetUserId)
            val isBlocked = chatRepository.isUserBlocked(args.targetUserId)

            mapToState(
                user = user,
                channelUserFollows = channelUserFollows,
                currentUserFollows = currentUserFollows,
                isBlocked = isBlocked
            )
        }

        val state = result.getOrElse { UserPopupState.Error(it) }
        _userPopupState.value = state
    }

    private fun mapToState(user: HelixUserDto?, channelUserFollows: UserFollowsDto?, currentUserFollows: UserFollowsDto?, isBlocked: Boolean): UserPopupState {
        user ?: return UserPopupState.Error()

        return UserPopupState.Success(
            userId = user.id,
            userName = user.name,
            displayName = user.displayName,
            avatarUrl = user.avatarUrl,
            created = user.createdAt.asParsedZonedDateTime(),
            isFollowing = currentUserFollows?.total == 1,
            followingSince = channelUserFollows?.data?.firstOrNull()?.followedAt?.asParsedZonedDateTime(),
            isBlocked = isBlocked
        )
    }
}