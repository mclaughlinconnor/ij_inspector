package com.mclaughlinconnor.ijInspector.utils

class RequestId {
    companion object {
        private var requestId = 0

        fun getNextRequestId(): Int {
            return requestId++
        }
    }
}