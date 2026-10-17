package dev.pogo.pocket

import android.content.Context
import android.content.pm.PackageManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import dalvik.system.DexFile
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Modifier

/**
 * Checks that R8 kept what Android and our libraries create by reflection, so
 * it's meant to run against the minified build too (CI does, with
 * -Ppogo.testBuildType=minified). It reaches the app only by name or through
 * library types, never through app classes, so the test itself keeps nothing.
 */
@RunWith(AndroidJUnit4::class)
class ReleaseSmokeTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext

    @Test
    fun manifestComponentsHaveConstructors() {
        val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_RECEIVERS or
            PackageManager.GET_SERVICES or PackageManager.GET_PROVIDERS
        val info = context.packageManager.getPackageInfo(context.packageName, flags)
        val names = listOfNotNull(info.applicationInfo?.className) +
            info.activities.orEmpty().map { it.name } + info.receivers.orEmpty().map { it.name } +
            info.services.orEmpty().map { it.name } + info.providers.orEmpty().map { it.name }
        val missing = names.filter { runCatching { load(it).getDeclaredConstructor() }.isFailure }
        assertTrue("no no-arg constructor: $missing", missing.isEmpty())
    }

    // Glance runs actionRunCallback actions with Class.forName(name)
    // .getDeclaredConstructor().newInstance(), so a missing constructor makes
    // the tap do nothing.
    @Test
    fun widgetActionsHaveConstructors() {
        val actions = appClasses().filter { ActionCallback::class.java.isAssignableFrom(it) && it.isConcrete() }
        assertTrue("found no widget actions", actions.size >= 2)
        val missing = actions.filter { runCatching { it.getDeclaredConstructor().newInstance() }.isFailure }
        assertTrue("can't create: ${missing.map { it.name }}", missing.isEmpty())
    }

    // WorkManager creates workers through this constructor.
    @Test
    fun workersHaveConstructors() {
        val workers = appClasses().filter { ListenableWorker::class.java.isAssignableFrom(it) && it.isConcrete() }
        assertTrue("found no workers", workers.isNotEmpty())
        val missing = workers.filter {
            runCatching { it.getDeclaredConstructor(Context::class.java, WorkerParameters::class.java) }.isFailure
        }
        assertTrue("no (Context, WorkerParameters) constructor: ${missing.map { it.name }}", missing.isEmpty())
    }

    @Test
    fun appOpens() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)!!
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = instrumentation.startActivitySync(intent)
        instrumentation.waitForIdleSync()
        assertNotNull(activity)
        assertTrue("the app closed right after opening", !activity.isFinishing)
        activity.finish()
    }

    private fun load(name: String): Class<*> = Class.forName(name, false, context.classLoader)

    private fun Class<*>.isConcrete() = !isInterface && !Modifier.isAbstract(modifiers)

    // Every class in the app's APK, including libraries, under whatever names R8 gave them.
    @Suppress("DEPRECATION")
    private fun appClasses(): List<Class<*>> =
        DexFile(context.packageCodePath).entries().toList()
            .filterNot { it.startsWith("kotlin") || it.startsWith("java") }
            .mapNotNull { runCatching { load(it) }.getOrNull() }
}
