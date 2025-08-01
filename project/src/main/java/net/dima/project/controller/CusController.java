// [✅ 이 코드로 CusController.java 파일 전체를 교체해주세요]

package net.dima.project.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.Authentication;
import net.dima.project.service.RequestService;
import net.dima.project.service.TransactionHistoryService;
import net.dima.project.dto.MyPostedRequestDto;
import net.dima.project.dto.TransactionHistoryDto;

import org.springframework.data.domain.Sort;

@Controller
@RequestMapping("/cus")
@RequiredArgsConstructor
@Slf4j
public class CusController {

    private final RequestService requestService; 
    private final TransactionHistoryService transactionHistoryService;
    
 // [✅ cusRequest 메서드를 아래 코드로 교체해주세요]
    @GetMapping("/cusRequest")
    public String cusRequest(Model model, Authentication authentication,
                             @RequestParam(name = "status", required = false) String status, // [✅ 추가]
                             @PageableDefault(size = 5, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        
        String userId = authentication.getName();
        Page<MyPostedRequestDto> requestPage = requestService.getRequestsForShipper(userId, status, pageable); // [✅ 수정]

        model.addAttribute("requestPage", requestPage);
        model.addAttribute("activeMenu", "cusRequest");
        model.addAttribute("status", status); // [✅ 추가]
        
        return "cus/CUS_request";
    }

    /**
     * [추가] 화주의 요청 이력 조회 페이지를 반환하는 메소드
     */
    @GetMapping("/cusHistory")
    public String cusHistoryPage(Model model, Authentication authentication, Pageable pageable) {
        String userId = authentication.getName();
        Page<TransactionHistoryDto> historyPage = transactionHistoryService.getShipperHistory(userId, pageable);

        model.addAttribute("historyPage", historyPage);
        model.addAttribute("activeMenu", "cusHistory");

        return "cus/CUS_History";
    }
}