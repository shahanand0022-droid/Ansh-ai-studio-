package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.StudioConstants
import com.example.data.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class StudioRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun observeMyProjects_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
    auth.signOut()
    val repository = StudioRepository(firestore)

    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        repository.observeMyProjects("some_uid").first()
      }
      fail("Expected PERMISSION_DENIED for unauthenticated user")
    } catch (e: Exception) {
      val firestoreException = checkNotNull(e.unwrapFirestoreException()) {
        "Expected nested FirebaseFirestoreException, got $e"
      }
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
    }
  }

  @Test
  fun redeemInvitation_withInvalidCode_failsWithCleanMessage() = runBlocking {
    val uid = signInTestUser("visitor_${System.currentTimeMillis()}@test.com")
    val repository = StudioRepository(firestore)

    val result = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.redeemInvitationAtomically(
        uid = uid,
        email = "visitor@test.com",
        displayName = "Visitor",
        photoUrl = "",
        rawInviteCode = "HAFG",
      )
    }
    assertTrue(result.isFailure)
    val msg = result.exceptionOrNull()?.message.orEmpty()
    assertTrue("Expected helpful message, got: $msg", msg.contains("HAFG") && msg.contains("not found"))
  }

  @Test
  fun user_cannotDirectlySelfRegisterWithoutInvitation() = runBlocking {
    val uid = signInTestUser("uninvited_${System.currentTimeMillis()}@test.com")
    val ts = com.google.firebase.Timestamp.now()
    try {
      withTimeout(DEFAULT_TIMEOUT_MS) {
        firestore.collection("users").document(uid).set(
          mapOf(
            "userId" to uid,
            "email" to "uninvited@test.com",
            "displayName" to "Uninvited",
            "role" to StudioConstants.ROLE_USER,
            "status" to StudioConstants.STATUS_ACTIVE,
            "planId" to StudioConstants.PLAN_FREE,
            "videoUsed" to 0L,
            "voiceUsed" to 0L,
            "imageUsed" to 0L,
            "scriptUsed" to 0L,
            "createdAt" to ts,
            "updatedAt" to ts,
          ),
        ).await()
      }
      fail("Expected PERMISSION_DENIED when self-registering without an invitation")
    } catch (e: FirebaseFirestoreException) {
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
    }
  }

  @Test
  fun crossUser_cannotReadAnotherUsersProfileOrProjects() = runBlocking {
    val aliceUid = signInTestUser("alice_${System.currentTimeMillis()}@test.com")
    val bobUid = signInTestUser("bob_${System.currentTimeMillis()}@test.com")

    // Bob tries to read Alice's user doc and create a project stamped with Alice's UID
    try {
      withTimeout(DEFAULT_TIMEOUT_MS) {
        firestore.collection("users").document(aliceUid).get().await()
      }
      fail("Expected PERMISSION_DENIED when Bob reads Alice's profile")
    } catch (e: FirebaseFirestoreException) {
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
    }

    val repository = StudioRepository(firestore)
    val fakeAliceUser = UserProfile(userId = aliceUid, email = "alice@test.com", displayName = "Alice")
    val spoofResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.saveProjectAndIncrementUsage(
        user = fakeAliceUser,
        title = "Spoofed Project",
        type = StudioConstants.TYPE_SCRIPT,
        prompt = "Test",
        outputText = "Output",
        language = "en",
        modelUsed = "gemini-3.8-flash",
      )
    }
    assertTrue(spoofResult.isFailure)
    val ex = spoofResult.exceptionOrNull()?.unwrapFirestoreException()
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, ex?.code)
  }

  @Test
  fun verifyAdminAccess_regularOrUnverifiedUser_returnsFalse() = runBlocking {
    val uid = signInTestUser("regular_${System.currentTimeMillis()}@test.com")
    val repository = StudioRepository(firestore)

    val unverifiedResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.verifyAdminAccess(uid, "shahprince0021@gmail.com", emailVerified = false)
    }
    assertTrue(unverifiedResult.isSuccess)
    assertEquals(false, unverifiedResult.getOrThrow())

    val nonAdminResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.verifyAdminAccess(uid, "regular@test.com", emailVerified = true)
    }
    assertTrue(nonAdminResult.isSuccess)
    assertEquals(false, nonAdminResult.getOrThrow())
  }

  @Test
  fun observeChatAndNotes_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
    auth.signOut()
    val repository = StudioRepository(firestore)

    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        repository.observeChatSessions("some_uid").first()
      }
      fail("Expected PERMISSION_DENIED for unauthenticated chat_sessions query")
    } catch (e: Exception) {
      val firestoreException = checkNotNull(e.unwrapFirestoreException()) {
        "Expected nested FirebaseFirestoreException, got $e"
      }
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
    }

    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        repository.observeChatMessages("sess_1", "some_uid").first()
      }
      fail("Expected PERMISSION_DENIED for unauthenticated messages query")
    } catch (e: Exception) {
      val firestoreException = checkNotNull(e.unwrapFirestoreException()) {
        "Expected nested FirebaseFirestoreException, got $e"
      }
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
    }

    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        repository.observeAssistantNotes("some_uid").first()
      }
      fail("Expected PERMISSION_DENIED for unauthenticated assistant_notes query")
    } catch (e: Exception) {
      val firestoreException = checkNotNull(e.unwrapFirestoreException()) {
        "Expected nested FirebaseFirestoreException, got $e"
      }
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
    }
  }

  @Test
  fun chatNotesAndConfigWrites_unprivilegedOrCrossUser_failWithPermissionDenied() = runBlocking {
    val bobUid = signInTestUser("bob_chat_${System.currentTimeMillis()}@test.com")
    val repository = StudioRepository(firestore)

    // Uninvited user (no active users/{uid} doc) cannot create chat_sessions or assistant_notes
    val chatResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.createChatSession(
        uid = bobUid,
        title = "Unauthorized Session",
        language = "en",
        modelUsed = "gemini-3.8-flash",
      )
    }
    assertTrue(chatResult.isFailure)
    assertEquals(
      FirebaseFirestoreException.Code.PERMISSION_DENIED,
      chatResult.exceptionOrNull()?.unwrapFirestoreException()?.code,
    )

    val noteResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.createAssistantNote(
        uid = bobUid,
        title = "Unauthorized Note",
        content = "Should fail",
        kind = "note",
      )
    }
    assertTrue(noteResult.isFailure)
    assertEquals(
      FirebaseFirestoreException.Code.PERMISSION_DENIED,
      noteResult.exceptionOrNull()?.unwrapFirestoreException()?.code,
    )

    // Non-admin user cannot mutate global system_config
    val configResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.updateSystemConfig(
        adminUid = bobUid,
        adminEmail = "bob@test.com",
        config = com.example.data.model.SystemConfig(
          aiChatEnabled = false,
          maxVideoDurationSeconds = 180,
        ),
      )
    }
    assertTrue(configResult.isFailure)
    assertEquals(
      FirebaseFirestoreException.Code.PERMISSION_DENIED,
      configResult.exceptionOrNull()?.unwrapFirestoreException()?.code,
    )

    // Bob cannot read or append messages inside Alice's session path
    val spoofMsgResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.appendChatMessage(
        sessionId = "alice_private_sess",
        uid = "alice_uid",
        role = "user",
        text = "Spoofed message",
        hasImageAttachment = false,
        modelUsed = "gemini-3.8-flash",
      )
    }
    assertTrue(spoofMsgResult.isFailure)
    assertEquals(
      FirebaseFirestoreException.Code.PERMISSION_DENIED,
      spoofMsgResult.exceptionOrNull()?.unwrapFirestoreException()?.code,
    )
  }

  private companion object {
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 3000L
  }
}

