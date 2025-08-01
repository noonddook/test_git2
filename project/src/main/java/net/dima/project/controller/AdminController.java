package net.dima.project.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequestMapping("/adm") // 이 컨트롤러의 모든 요청은 /adm으로 시작합니다.
@Slf4j
public class AdminController {

    /**
     * 관리자 대시보드 메인 페이지를 반환합니다.
     */
    @GetMapping("/main")
    public String adminTotalPage() {
        log.info("관리자 대시보드(물동량조회) 접근");
        // /templates/adm/ADM_total.html 파일을 찾아서 반환합니다.
        return "adm/ADM_main";
    }
}