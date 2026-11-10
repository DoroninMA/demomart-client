package com.demomart.client.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.demomart.client.account.AccountRepository
import com.demomart.client.net.ApiResult
import com.demomart.client.net.errorText
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(private val repository: AccountRepository) : ViewModel() {
    val account = repository.account
    private val mutableRefreshing = MutableStateFlow(false)
    val refreshing = mutableRefreshing.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()

    fun refresh() {
        if (mutableRefreshing.value) return
        mutableRefreshing.value = true
        viewModelScope.launch {
            try {
                val result = repository.refresh()
                mutableError.value = (result as? ApiResult.Err)?.let { errorText(it.code) }
            } finally {
                mutableRefreshing.value = false
            }
        }
    }
}
