package com.example.nossasaudeapp.sync

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.nossasaudeapp.BuildConfig
import com.example.nossasaudeapp.data.local.NossaSaudeDatabase
import com.example.nossasaudeapp.data.remote.ErrorHandlingInterceptor
import com.example.nossasaudeapp.data.remote.HeadersInterceptor
import com.example.nossasaudeapp.data.remote.api.ConsultationsApi
import com.example.nossasaudeapp.data.remote.api.MembersApi
import com.example.nossasaudeapp.data.repository.ConsultationRepository
import com.example.nossasaudeapp.data.repository.MemberRepository
import com.example.nossasaudeapp.domain.model.Doctor
import com.example.nossasaudeapp.domain.model.Exam
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Retrofit
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Testes instrumentados de sync end-to-end.
 * Requerem o backend LocalStack rodando em ${BuildConfig.API_BASE_URL}.
 * São pulados automaticamente se o backend não estiver acessível.
 *
 * Cada teste registra os IDs locais criados e os deleta no @After via soft-delete
 * + push, para não poluir os dados de testes manuais no servidor.
 */
@RunWith(AndroidJUnit4::class)
class SyncRoundTripTest {

    private lateinit var db: NossaSaudeDatabase
    private lateinit var memberRepository: MemberRepository
    private lateinit var consultationRepository: ConsultationRepository
    private lateinit var consultationsApi: ConsultationsApi

    // IDs locais (Room UUID) criados durante cada teste — limpos no @After
    private val createdConsultationIds = mutableListOf<String>()
    private val createdMemberIds = mutableListOf<String>()

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, NossaSaudeDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val client = OkHttpClient.Builder()
            .addInterceptor(HeadersInterceptor(BuildConfig.API_KEY, BuildConfig.FAMILY_ID))
            .addInterceptor(ErrorHandlingInterceptor(json))
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        val membersApi = retrofit.create(MembersApi::class.java)
        consultationsApi = retrofit.create(ConsultationsApi::class.java)

        val io = Dispatchers.IO
        memberRepository = MemberRepository(db.memberDao(), membersApi, io)
        consultationRepository = ConsultationRepository(
            db.consultationDao(), db.memberDao(), db.searchDao(), consultationsApi, io,
        )
    }

    /**
     * Soft-deleta no servidor todos os registros criados durante o teste,
     * na ordem correta (consultas antes de membros).
     */
    @After
    fun tearDown() = runBlocking {
        createdConsultationIds.forEach { id ->
            runCatching {
                consultationRepository.delete(id)
                consultationRepository.pushDirty(id)
            }
        }
        createdMemberIds.forEach { id ->
            runCatching {
                memberRepository.delete(id)
                memberRepository.pushDirty(id)
            }
        }
        db.close()
    }

    // ── member push ────────────────────────────────────────────────────────────

    @Test
    fun memberPush_assignsRemoteId() = runBlocking {
        assumeBackendReachable()

        val member = memberRepository.create(
            name = "Test User ${UUID.randomUUID()}",
            birthDate = null, bloodType = null, weightKg = null, heightCm = null,
            allergies = emptyList(), chronicConditions = emptyList(),
        ).also { createdMemberIds += it.id }

        assertNull("remoteId deve ser null antes do push", member.remoteId)

        memberRepository.pushDirty(member.id)

        val synced = memberRepository.getById(member.id)
        assertNotNull("remoteId deve ser preenchido após push", synced?.remoteId)
    }

    // ── consultation push ──────────────────────────────────────────────────────

    /**
     * Verifica o bug #13: consultationPush deve enviar member.remoteId (MongoDB _id)
     * como memberId, não o UUID local do Room.
     */
    @Test
    fun consultationPush_sendsMemberRemoteIdToServer_notLocalUUID() = runBlocking {
        assumeBackendReachable()

        val member = memberRepository.create(
            name = "Membro Sync ${UUID.randomUUID()}",
            birthDate = null, bloodType = null, weightKg = null, heightCm = null,
            allergies = emptyList(), chronicConditions = emptyList(),
        ).also { createdMemberIds += it.id }

        memberRepository.pushDirty(member.id)
        val syncedMember = memberRepository.getById(member.id)!!
        assertNotNull(syncedMember.remoteId)

        val consultation = consultationRepository.create(
            memberId = member.id,
            date = Clock.System.now(),
            reason = "Rotina integração",
            doctor = Doctor(null, null, null),
            clinic = null, notes = null,
            tags = emptyList(), returnOf = null,
            medications = emptyList(), exams = emptyList(),
        ).also { createdConsultationIds += it.id }

        consultationRepository.pushDirty(consultation.id)

        val syncedConsultation = consultationRepository.getById(consultation.id)!!
        assertNotNull("consultation.remoteId deve ser preenchido após push", syncedConsultation.remoteId)

        val fromServer = consultationsApi.getById(syncedConsultation.remoteId!!)
        assertEquals(
            "memberId no servidor deve ser o remoteId do membro (não o UUID local do Room)",
            syncedMember.remoteId,
            fromServer.memberId,
        )
    }

    /**
     * Verifica o bug #25: reconcileRemote() deve preservar remoteId dos exames
     * usando UPDATE direto, sem delete+reinsert que apagaria result_images.
     */
    @Test
    fun consultationPush_reconcileExamRemoteIds_afterPush() = runBlocking {
        assumeBackendReachable()

        val member = memberRepository.create(
            name = "Exam Test ${UUID.randomUUID()}",
            birthDate = null, bloodType = null, weightKg = null, heightCm = null,
            allergies = emptyList(), chronicConditions = emptyList(),
        ).also { createdMemberIds += it.id }

        memberRepository.pushDirty(member.id)

        val consultation = consultationRepository.create(
            memberId = member.id,
            date = Clock.System.now(),
            reason = "Exames",
            doctor = Doctor(null, null, null),
            clinic = null, notes = null,
            tags = emptyList(), returnOf = null,
            medications = emptyList(),
            exams = listOf(
                Exam(
                    id = UUID.randomUUID().toString(),
                    remoteId = null,
                    name = "Hemograma",
                    notes = null,
                    resultImages = emptyList(),
                ),
            ),
        ).also { createdConsultationIds += it.id }

        consultationRepository.pushDirty(consultation.id)

        val synced = consultationRepository.getById(consultation.id)!!
        assertNotNull("consultation.remoteId deve ser preenchido", synced.remoteId)
        assertNotNull(
            "exam.remoteId deve ser preenchido via reconcileRemote()",
            synced.exams.firstOrNull()?.remoteId,
        )
    }

    // ── pull ───────────────────────────────────────────────────────────────────

    /**
     * Verifica o bug #14: savePulled() deve resolver dto.memberId (remoteId do servidor)
     * para o UUID local do membro antes de salvar a consulta.
     */
    @Test
    fun pull_resolvesRemoteMemberIdToLocalUUID() = runBlocking {
        assumeBackendReachable()

        val member = memberRepository.create(
            name = "Pull Test ${UUID.randomUUID()}",
            birthDate = null, bloodType = null, weightKg = null, heightCm = null,
            allergies = emptyList(), chronicConditions = emptyList(),
        ).also { createdMemberIds += it.id }

        memberRepository.pushDirty(member.id)
        val syncedMember = memberRepository.getById(member.id)!!

        val consultation = consultationRepository.create(
            memberId = member.id,
            date = Clock.System.now(),
            reason = "Pull Test",
            doctor = Doctor(null, null, null),
            clinic = null, notes = null,
            tags = emptyList(), returnOf = null,
            medications = emptyList(), exams = emptyList(),
        ).also { createdConsultationIds += it.id }

        consultationRepository.pushDirty(consultation.id)
        val synced = consultationRepository.getById(consultation.id)!!

        val dto = consultationsApi.getById(synced.remoteId!!)
        assertEquals(syncedMember.remoteId, dto.memberId)

        consultationRepository.savePulled(dto)

        val local = consultationRepository.getById(consultation.id)
        assertNotNull("Consulta deve existir localmente após savePulled()", local)
        assertEquals(
            "memberId local deve ser o UUID do Room, não o remoteId",
            member.id,
            local!!.memberId,
        )
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private fun assumeBackendReachable() {
        try {
            val url = URL(BuildConfig.API_BASE_URL)
            val port = url.port.takeIf { it > 0 } ?: 80
            Socket().use { it.connect(InetSocketAddress(url.host, port), 3_000) }
        } catch (_: Exception) {
            Assume.assumeTrue("Backend LocalStack não está acessível — pulando teste", false)
        }
    }
}
