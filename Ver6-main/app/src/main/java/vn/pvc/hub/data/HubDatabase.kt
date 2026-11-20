package vn.pvc.hub.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [HubItem::class], version = 3, exportSchema = false)
abstract class HubDatabase : RoomDatabase() {
    abstract fun dao(): HubDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE hub_item ADD COLUMN replyTo TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE hub_item ADD COLUMN snoozedUntil INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE hub_item ADD COLUMN hidden INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile
        private var instance: HubDatabase? = null

        fun get(context: Context): HubDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    HubDatabase::class.java,
                    "pvc_hub.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
            }
    }
}
