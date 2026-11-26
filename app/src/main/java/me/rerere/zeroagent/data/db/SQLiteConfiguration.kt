package me.rerere.zeroagent.data.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory
import io.requery.android.database.sqlite.SQLiteCustomExtension
import io.requery.android.database.sqlite.SQLiteDatabaseConfiguration

/** Use the same SQLite extensions for the live database and backup validation. */
internal object SQLiteConfiguration {
    const val DATABASE_NAME = "rikka_hub"

    fun configure(context: Context, configuration: SQLiteDatabaseConfiguration): SQLiteDatabaseConfiguration {
        configuration.customExtensions.add(
            SQLiteCustomExtension(context.applicationInfo.nativeLibraryDir + "/libsimple", null)
        )
        return configuration
    }

    // The assignment returns a row. Read it on the transaction's write connection.
    fun enableSecureDelete(database: SupportSQLiteDatabase) {
        database.query("PRAGMA secure_delete=ON").use { cursor ->
            check(cursor.moveToFirst() && cursor.getInt(0) == 1) {
                "Could not enable SQLite secure deletion"
            }
        }
    }

    fun openHelperFactory(context: Context) = RequerySQLiteOpenHelperFactory(
        listOf(RequerySQLiteOpenHelperFactory.ConfigurationOptions { configure(context, it) })
    )
}
