package com.immunoblockers.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName="wellbeing_entries")
data class WellbeingEntry(@PrimaryKey val id:String, val recordedAt:Long, val mood:String?=null, val text:String="", val groupId:String?=null)
@Entity(tableName="wellbeing_links",primaryKeys=["entryId","occurrenceId"])
data class WellbeingLink(val entryId:String,val occurrenceId:String)
@Entity(tableName="survey_groups")
data class SurveyGroup(@PrimaryKey val id:String,val firstAt:Long,val dueAt:Long,val status:String="pending")
@Entity(tableName="survey_members",primaryKeys=["groupId","occurrenceId"],indices=[Index(value=["occurrenceId"],unique=true)])
data class SurveyMember(val groupId:String,val occurrenceId:String)
@Dao interface WellbeingDao {
 @Query("SELECT * FROM wellbeing_entries ORDER BY recordedAt DESC") fun observe():Flow<List<WellbeingEntry>>
 @Query("SELECT * FROM wellbeing_entries ORDER BY recordedAt DESC LIMIT 1") suspend fun latest():WellbeingEntry?
 @Query("SELECT * FROM wellbeing_entries WHERE id=:id") suspend fun entry(id:String):WellbeingEntry?
 @Upsert suspend fun save(entry:WellbeingEntry)
 @Query("DELETE FROM wellbeing_entries WHERE id=:id") suspend fun delete(id:String)
 @Query("DELETE FROM wellbeing_links WHERE entryId=:id") suspend fun deleteLinks(id:String)
 @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun link(links:List<WellbeingLink>)
 @Query("SELECT * FROM survey_groups") suspend fun groups():List<SurveyGroup>
 @Query("SELECT * FROM survey_groups WHERE id=:id") suspend fun group(id:String):SurveyGroup?
 @Upsert suspend fun saveGroup(group:SurveyGroup)
 @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun member(member:SurveyMember)
 @Query("DELETE FROM survey_members WHERE groupId=:id") suspend fun deleteMembers(id:String)
 @Query("DELETE FROM survey_groups WHERE id=:id") suspend fun deleteGroup(id:String)
 @Query("SELECT occurrenceId FROM survey_members WHERE groupId=:id") suspend fun members(id:String):List<String>
}
val MIGRATION_1_2=object:Migration(1,2) {
 override fun migrate(db:SupportSQLiteDatabase) {
  db.execSQL("CREATE TABLE IF NOT EXISTS wellbeing_entries (id TEXT NOT NULL PRIMARY KEY, recordedAt INTEGER NOT NULL, mood TEXT, text TEXT NOT NULL, groupId TEXT)")
  db.execSQL("CREATE TABLE IF NOT EXISTS wellbeing_links (entryId TEXT NOT NULL, occurrenceId TEXT NOT NULL, PRIMARY KEY(entryId, occurrenceId))")
  db.execSQL("CREATE TABLE IF NOT EXISTS survey_groups (id TEXT NOT NULL PRIMARY KEY, firstAt INTEGER NOT NULL, dueAt INTEGER NOT NULL, status TEXT NOT NULL)")
  db.execSQL("CREATE TABLE IF NOT EXISTS survey_members (groupId TEXT NOT NULL, occurrenceId TEXT NOT NULL, PRIMARY KEY(groupId, occurrenceId))")
  db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_survey_members_occurrenceId ON survey_members (occurrenceId)")
 }
}
