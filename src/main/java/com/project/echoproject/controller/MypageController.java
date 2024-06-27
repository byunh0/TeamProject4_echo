package com.project.echoproject.controller;

import com.project.echoproject.dto.ChangePasswordForm;
import com.project.echoproject.dto.PointDTO;
import com.project.echoproject.dto.UseAmountForm;
import com.project.echoproject.entity.Point;
import com.project.echoproject.entity.SiteUser;
import com.project.echoproject.entity.UseAmount;
import com.project.echoproject.service.*;

import org.springframework.data.domain.Page;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/mypage")
public class MypageController {

    private final MypageService mypageService;
    private final ChangePasswordServiceImpl changePasswordServiceImpl;
    private final UseAmountServiceImpl useAmountServiceImpl;
    private final PointService pointService;
    private final SiteUserServiceImpl siteUserServiceImpl;

    @Autowired
    public MypageController(MypageService mypageService,
                            ChangePasswordServiceImpl changePasswordServiceImpl,
                            UseAmountServiceImpl useAmountServiceImpl, PointService pointService, SiteUserServiceImpl siteUserServiceImpl) {
        this.mypageService = mypageService;
        this.changePasswordServiceImpl = changePasswordServiceImpl;
        this.useAmountServiceImpl = useAmountServiceImpl;
        this.pointService = pointService;
        this.siteUserServiceImpl = siteUserServiceImpl;
    }


    @PreAuthorize("isAuthenticated()")
    @GetMapping("/edit/{userId}")
    public String editPersonalInfo(@PathVariable String userId, Model model, Principal principal) {
        if (!principal.getName().equals(userId)) {
            return "redirect:/";
        }
        SiteUser user = mypageService.getUserById(userId);
        model.addAttribute("user", user);
        return "edit_form";
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/edit/{userId}")
    public String updatePersonalInfo(@PathVariable String userId,
                                     @ModelAttribute SiteUser updatedUser,
                                     @RequestParam(value = "file", required = false) MultipartFile file,
                                     Model model, Principal principal) {
        if (!principal.getName().equals(userId)) {
            return "redirect:/";
        }
        try {
            mypageService.updateUser(userId, updatedUser, file);
            return "redirect:/mypage/" + userId;
        } catch (IOException e) {
            model.addAttribute("errorMessage", "이미지 업로드 중 오류가 발생했습니다: " + e.getMessage());
            return "edit_form";
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "edit_form";
        }
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{userId}")
    public String myPage(@PathVariable String userId, Model model, Principal principal) {
        if (!principal.getName().equals(userId)) {
            return "redirect:/";
        }
        SiteUser user = mypageService.getUserById(userId);
        model.addAttribute("user", user);
        return "mypage";
    }

    // 비밀번호 변경 폼을 표시하는 메소드
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/change-password/{userId}")
    public String changePasswordPage(@PathVariable String userId, Model model,Principal principal) {
        if (!principal.getName().equals(userId)) {
            return "redirect:/";
        }
        model.addAttribute("userId", userId);
        model.addAttribute("changePasswordForm", new ChangePasswordForm());
        return "changepw_form";
    }


    // 비밀번호 변경 요청을 처리하는 메소드
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/change-password/{userId}")
    public String changePassword(@PathVariable String userId,
                                 @ModelAttribute("changePasswordForm") @Validated ChangePasswordForm changePwForm,
                                 BindingResult result, Model model,Principal principal) {
        if (!principal.getName().equals(userId)) {
            return "redirect:/";
        }
        if (result.hasErrors()) {
            model.addAttribute("userId", userId);
            return "changepw_form";
        }

        try {
            changePasswordServiceImpl.changePassword(userId, changePwForm);
        } catch (IllegalArgumentException e) {
            result.rejectValue("currentPassword", "error.form", e.getMessage());
            model.addAttribute("userId", userId);
            return "changepw_form";
        }

        return "redirect:/mypage/" + userId;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    @PostMapping("/delete/{userId}")
    public String deleteUser(@PathVariable String userId, Principal principal,
                             HttpServletRequest request, HttpServletResponse response) {
        if (!principal.getName().equals(userId)) {
            return "redirect:/";
        }

        mypageService.deleteUser(userId);

        // 로그아웃 처리
        SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();
        logoutHandler.logout(request, response, SecurityContextHolder.getContext().getAuthentication());

        return "redirect:/";
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/input-useamount/{userId}")
    public String showUsageForm(@PathVariable String userId, Model model, Principal principal) {
        if (!principal.getName().equals(userId)) {
            return "redirect:/";
        }
        try {
            UseAmountForm useAmountForm = new UseAmountForm();
            model.addAttribute("useAmountForm", useAmountForm);
            model.addAttribute("userId", userId);

            LocalDate currentDate = LocalDate.now();
            model.addAttribute("currentDate", currentDate);

            return "useamount_form";
        } catch (Exception e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "error";
        }
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/input-useamount/{userId}")
    public String processUsageForm(@PathVariable String userId,
                                   @ModelAttribute("useAmountForm") UseAmountForm useAmountForm,
                                   BindingResult bindingResult,
                                   Model model, Principal principal) {
        if (!principal.getName().equals(userId)) {
            return "redirect:/";
        }
        LocalDate currentDate = LocalDate.now();
        if (!useAmountForm.getUseDate().equals(currentDate)) {
            bindingResult.rejectValue("useDate", "error.useDate", "날짜는 오늘이어야 합니다.");
            model.addAttribute("userId", userId);
            return "useamount_form";
        }

        try {
            useAmountServiceImpl.saveUseAmount(userId, useAmountForm);
            return "redirect:/mypage/useamount-detail/" + userId + "?year=" + currentDate.getYear();
        } catch (RuntimeException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("userId", userId);
            return "useamount_form";
        }
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/useamount-detail/{userId}")
    public String showUseAmountDetail(@PathVariable String userId,
                                      @RequestParam(required = false) Integer year,
                                      Model model, Principal principal) {
        if (!principal.getName().equals(userId)) {
            return "redirect:/";
        }
        try {
            int currentYear = (year != null) ? year : LocalDate.now().getYear();

            Map<Integer, UseAmount> useAmounts = useAmountServiceImpl.getMonthlyUseAmounts(userId, currentYear);
            model.addAttribute("useAmounts", useAmounts);
            model.addAttribute("userId", userId);
            model.addAttribute("year", currentYear);
            return "useamount_detail";
        } catch (Exception e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "error";
        }
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/point-status/{userId}")
    public String showPointHistory(@PathVariable String userId,
                                   Model model,
                                   Principal principal,
                                   @RequestParam(defaultValue = "0") int page) {
        if (!principal.getName().equals(userId)) {
            return "redirect:/";
        }

        int size = 9; // 페이지당 표시할 항목 수
        long totalItems = pointService.countPointHistoryByUserId(userId);

        if (totalItems <= 9) {
            // 9개 이하일 경우 전체 리스트 반환
            List<Point> pointHistory = pointService.getPointHistoryByUserId(userId);
            model.addAttribute("pointHistory", pointHistory);
            model.addAttribute("totalPages", 1);
        } else {
            // 9개 초과 시 페이징 처리
            Page<Point> pointHistoryPage = pointService.getPointHistoryByUserIdPaged(userId, page, size);
            model.addAttribute("pointHistory", pointHistoryPage.getContent());
            model.addAttribute("totalPages", pointHistoryPage.getTotalPages());
        }

        model.addAttribute("currentPage", page);
        return "point_status";
    }

    @GetMapping("/challenge")
    public String myChallenge(Model model) {
        return "challenge_status";
    }

    @GetMapping("/coupon")
    public String coupon(Model model) {
        return "coupon_status";
    }
}

