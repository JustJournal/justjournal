package com.justjournal.ctl.admin;

import com.justjournal.exception.BadRequestException;
import com.justjournal.services.AdminSettingsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Admin page for the site settings: lists every settings row and changes the value of one at a
 * time. Secret values are never sent to the browser.
 *
 * @author Lucas Holt
 */
@Controller
@RequestMapping("/admin/settings")
public class AdminSettingsController {

  static final String REDIRECT = "redirect:/admin/settings";

  private final AdminSettingsService adminSettingsService;

  public AdminSettingsController(final AdminSettingsService adminSettingsService) {
    this.adminSettingsService = adminSettingsService;
  }

  @GetMapping
  public String list(final Model model) {
    model.addAttribute("title", "Settings");
    model.addAttribute("adminSection", "settings");
    model.addAttribute("contentTemplate", "admin/settings");
    model.addAttribute("settings", adminSettingsService.list());
    model.addAttribute("maxLength", AdminSettingsService.MAX_VALUE_LENGTH);
    return "admin/layout";
  }

  @PostMapping("/{id}")
  public String update(
      @PathVariable("id") final int id,
      @RequestParam(value = "value", required = false) final String value,
      final RedirectAttributes redirectAttributes) {
    try {
      final String name = adminSettingsService.update(id, value);
      redirectAttributes.addFlashAttribute("message", "Saved " + name + ".");
    } catch (final BadRequestException e) {
      redirectAttributes.addFlashAttribute("error", e.getMessage());
    }
    return REDIRECT;
  }
}
