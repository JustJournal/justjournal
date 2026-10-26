package com.justjournal.ctl;

import com.justjournal.Login;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class PublicPageController {

  private String renderPage(
      Model model, HttpSession session, String title, String pageId, String contentTemplate) {
    model.addAttribute("authenticatedUsername", Login.currentLoginName(session));
    model.addAttribute("title", title);
    model.addAttribute("pageId", pageId);
    model.addAttribute("contentTemplate", contentTemplate);
    return "layout";
  }

  @GetMapping("/cancel")
  public String cancel() {
    return "redirect:/prefs/journaldelete";
  }

  @GetMapping("/comment/{entryId}")
  public String comment(@PathVariable int entryId, Model model, HttpSession session) {
    model.addAttribute("entryId", entryId);
    return renderPage(model, session, "Just Journal: Comment", "comment", "fragments/public/comment");
  }

  @GetMapping("/comment/{entryId}/edit/{commentId}")
  public String editComment(
      @PathVariable int entryId, @PathVariable int commentId, Model model, HttpSession session) {
    model.addAttribute("entryId", entryId);
    model.addAttribute("commentId", commentId);
    return renderPage(
        model, session, "Just Journal: Edit Comment", "comment", "fragments/public/comment");
  }

  @GetMapping("/entry")
  public String entry(Model model, HttpSession session) {
    return renderPage(model, session, "Just Journal: New Entry", "entry", "fragments/public/entry");
  }

  @GetMapping("/entry/{entryId}")
  public String editEntry(@PathVariable int entryId, Model model, HttpSession session) {
    model.addAttribute("entryId", entryId);
    return renderPage(model, session, "Just Journal: Edit Entry", "entry", "fragments/public/entry");
  }

  @GetMapping("/members")
  public String members(Model model, HttpSession session) {
    return renderPage(model, session, "Just Journal: Members", "members", "fragments/public/members");
  }

  @GetMapping("/moodlist")
  public String moodList(Model model, HttpSession session) {
    return renderPage(model, session, "Just Journal: Mood List", "moodlist", "fragments/public/moodlist");
  }

  @GetMapping("/prefs")
  public String prefs(Model model, HttpSession session) {
    return renderPage(model, session, "Just Journal: Preferences", "prefs", "fragments/public/prefs");
  }

  @GetMapping("/prefs/avatar")
  public String prefsAvatar(Model model, HttpSession session) {
    return renderPage(
        model, session, "Just Journal: Avatar Preferences", "prefs-avatar", "fragments/public/prefs-avatar");
  }

  @GetMapping("/prefs/biography")
  public String prefsBiography(Model model, HttpSession session) {
    return renderPage(
        model,
        session,
        "Just Journal: Biography Preferences",
        "prefs-biography",
        "fragments/public/prefs-biography");
  }

  @GetMapping("/prefs/links")
  public String prefsLinks(Model model, HttpSession session) {
    return renderPage(
        model, session, "Just Journal: Link Preferences", "prefs-links", "fragments/public/prefs-links");
  }

  @GetMapping("/prefs/password")
  public String prefsPassword(Model model, HttpSession session) {
    return renderPage(
        model,
        session,
        "Just Journal: Password Preferences",
        "prefs-password",
        "fragments/public/prefs-password");
  }

  @GetMapping("/prefs/contact")
  public String prefsContact(Model model, HttpSession session) {
    return renderPage(
        model,
        session,
        "Just Journal: Contact Preferences",
        "prefs-contact",
        "fragments/public/prefs-contact");
  }

  @GetMapping("/prefs/image")
  public String prefsImage(Model model, HttpSession session) {
    return renderPage(
        model, session, "Just Journal: Image Preferences", "prefs-image", "fragments/public/prefs-image");
  }

  @GetMapping("/prefs/journal")
  public String prefsJournal(Model model, HttpSession session) {
    return renderPage(
        model,
        session,
        "Just Journal: Journal Preferences",
        "prefs-journal",
        "fragments/public/prefs-journal");
  }

  @GetMapping("/prefs/journaldelete")
  public String prefsJournalDelete(Model model, HttpSession session) {
    return renderPage(
        model,
        session,
        "Just Journal: Delete Account",
        "prefs-journaldelete",
        "fragments/public/prefs-journaldelete");
  }

  @GetMapping("/privacy")
  public String privacy(Model model, HttpSession session) {
    return renderPage(model, session, "Just Journal: Privacy", "privacy", "fragments/public/privacy");
  }

  @GetMapping("/profile/{username}")
  public String profile(@PathVariable String username, Model model, HttpSession session) {
    model.addAttribute("profileUsername", username);
    return renderPage(model, session, "Just Journal: Profile", "profile", "fragments/public/profile");
  }

  @GetMapping("/search")
  public String search(Model model, HttpSession session) {
    return renderPage(model, session, "Just Journal: Search", "search", "fragments/public/search");
  }

  @GetMapping("/sitemap")
  public String siteMap(Model model, HttpSession session) {
    return renderPage(model, session, "Just Journal: Sitemap", "sitemap", "fragments/public/sitemap");
  }

  @GetMapping("/software")
  public String software(Model model, HttpSession session) {
    return renderPage(model, session, "Just Journal: Software", "software", "fragments/public/software");
  }

  @GetMapping("/software/windows")
  public String softwareWindows(Model model, HttpSession session) {
    return renderPage(
        model, session, "Just Journal: Windows Client", "software-windows", "fragments/public/software-windows");
  }

  @GetMapping("/software/unix")
  public String softwareUnix(Model model, HttpSession session) {
    return renderPage(
        model, session, "Just Journal: Unix Client", "software-unix", "fragments/public/software-unix");
  }

  @GetMapping("/stats")
  public String stats(Model model, HttpSession session) {
    return renderPage(model, session, "Just Journal: Statistics", "stats", "fragments/public/stats");
  }

  @GetMapping("/support")
  public String support(Model model, HttpSession session) {
    return renderPage(model, session, "Just Journal: Support", "support", "fragments/public/support");
  }

  @GetMapping("/support/bugs")
  public String supportBugs(Model model, HttpSession session) {
    return renderPage(
        model, session, "Just Journal: Support Bugs", "support-bugs", "fragments/public/support-bugs");
  }

  @GetMapping("/support/faq")
  public String supportFaq(Model model, HttpSession session) {
    return renderPage(
        model, session, "Just Journal: Support FAQ", "support-faq", "fragments/public/support-faq");
  }

  @GetMapping("/support/api")
  public String supportApi(Model model, HttpSession session) {
    return renderPage(model, session, "Just Journal: Client API", "support-api", "fragments/public/support-api");
  }

  @GetMapping("/support/tech")
  public String supportTech(Model model, HttpSession session) {
    return renderPage(
        model, session, "Just Journal: Technical Information", "support-tech", "fragments/public/support-tech");
  }

  @GetMapping("/tags")
  public String tags(Model model, HttpSession session) {
    return renderPage(model, session, "Just Journal: Tags", "tags", "fragments/public/tags");
  }
}
