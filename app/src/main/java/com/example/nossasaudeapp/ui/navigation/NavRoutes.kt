package com.example.nossasaudeapp.ui.navigation

object NavRoutes {
    const val HOME = "home"
    const val MEMBER_NEW = "member/new"
    const val MEMBER_EDIT = "member/edit/{memberId}"
    const val MEMBER_PROFILE = "member/profile/{memberId}"
    const val CONSULTATION_NEW = "consultation/new/{memberId}"
    const val CONSULTATION_EDIT = "consultation/edit/{consultationId}"
    const val CONSULTATION_DETAIL = "consultation/detail/{consultationId}"
    const val SEARCH = "search"
    const val IMAGE_VIEWER = "image/viewer?keys={keys}&index={index}"
    const val SETTINGS = "settings"

    fun memberEdit(id: String) = "member/edit/$id"
    fun memberProfile(id: String) = "member/profile/$id"
    fun consultationNew(memberId: String) = "consultation/new/$memberId"
    fun consultationEdit(id: String) = "consultation/edit/$id"
    fun consultationDetail(id: String) = "consultation/detail/$id"

    /** Encode a list of URLs into the route, pipe-separated. Max 20 keys to avoid URI limits. */
    fun imageViewer(urls: List<String>, index: Int = 0): String {
        val encoded = urls.take(20)
            .joinToString("|") { android.net.Uri.encode(it) }
        return "image/viewer?keys=$encoded&index=$index"
    }
}
