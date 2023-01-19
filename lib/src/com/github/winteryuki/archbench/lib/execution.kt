package com.github.winteryuki.archbench.lib

import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

fun ExecutorService.submitCatching(onException: (Exception) -> Unit, runnable: Runnable): Future<*> =
    submit {
        try {
            runnable.run()
        } catch (e: Exception) {
            onException(e)
        }
    }
