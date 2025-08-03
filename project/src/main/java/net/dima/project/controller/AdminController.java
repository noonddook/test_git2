package net.dima.project.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dima.project.service.AdminService;
import net.dima.project.dto.ForwarderInfoDto; // import 추가


@Controller
@RequestMapping("/adm") // 이 컨트롤러의 모든 요청은 /adm으로 시작합니다.
@RequiredArgsConstructor
@Slf4j
public class AdminController {

	private final AdminService adminService;
    /**
     * 관리자 대시보드 메인 페이지를 반환합니다.
     */
    @GetMapping("/dashboard")
    public String adminDashboardPage(Model model) {
        log.info("관리자 대시보드 접근");
        // [수정] activeMenu 정보를 모델에 추가
        model.addAttribute("activeMenu", "dashboard");
        return "adm/ADM_dashboard";
    }

    @GetMapping("/forwarder-management")
    public String forwarderManagementPage(Model model) {
        // [수정] activeMenu 정보를 모델에 추가
        model.addAttribute("activeMenu", "forwarder-management");
        List<ForwarderInfoDto> forwarderList = adminService.getForwarderList();
        model.addAttribute("forwarderList", forwarderList);
        return "adm/ADM_forwarder_management";
    }
}