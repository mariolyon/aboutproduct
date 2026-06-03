package frontend.components

import com.raquo.laminar.api.L.{*, given}

object AppBanner:
  def apply(): HtmlElement =
    div(
      cls := "app-banner mb-6 p-4 bg-gray-900 text-gray-50 rounded-b-xl shadow-md",
      h1(cls := "text-3xl font-extrabold mb-1 text-white tracking-tight", "AboutProduct"),
      p(cls := "app-blurb text-gray-400 max-w-2xl mx-auto leading-relaxed", "AI-powered nutrition insights from any food label.")
    )
