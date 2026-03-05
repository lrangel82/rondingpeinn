package com.larangel.rondingpeinn.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class HomeViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "Para uso esclusivo de Rondinero de Guadalupe Inn"
    }
    val text: LiveData<String> = _text
}