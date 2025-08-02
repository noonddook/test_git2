// [✅ FWD_transaction_history.js 파일 전체를 이 코드로 교체]
document.addEventListener('DOMContentLoaded', () => {
    // --- 기본 요소 가져오기 ---
    const filterGroup = document.getElementById('transaction-filter-group');
    const tableBody = document.getElementById('transaction-list-body');
    const searchForm = document.getElementById('search-form');
    const keywordInput = document.getElementById('keyword-input');
    const startDateInput = document.getElementById('start-date');
    const endDateInput = document.getElementById('end-date');
    
    // [✅ 추가] 금액 계산 버튼 및 모달 요소
    const calcButton = document.getElementById('btn-calculate-summary');
    const summaryModal = document.getElementById('summary-modal');
    const summaryModalTitle = document.getElementById('summary-modal-title');
    const summaryModalBody = document.getElementById('summary-modal-body');

    // --- 데이터 렌더링 함수 (기존과 동일) ---
    async function fetchAndRenderTransactions() {
        // ... (이 함수 내용은 변경 없음) ...
    }
    
    // [✅ 추가] 날짜 선택 여부에 따라 계산 버튼 활성화/비활성화
    function updateCalcButtonState() {
        if (startDateInput.value && endDateInput.value) {
            calcButton.disabled = false;
        } else {
            calcButton.disabled = true;
        }
    }

    // [✅ 추가] 금액 계산 및 모달 표시 함수
    function showSummaryModal() {
        const rows = tableBody.querySelectorAll('tr');
        if (rows.length === 0 || (rows.length === 1 && rows[0].querySelector('td[colspan="7"]'))) {
            alert('계산할 데이터가 없습니다.');
            return;
        }

        const activeFilter = filterGroup.querySelector('.is-active').dataset.filter;
        let totalKRW = 0;
        let totalUSD = 0;
        let detailsHtml = '<table class="details-table"><thead><tr><th>품명</th><th>경로</th><th>금액</th></tr></thead><tbody>';

        rows.forEach(row => {
            const cells = row.querySelectorAll('td');
            const type = cells[1].textContent;
            const route = cells[2].textContent;
            const itemName = cells[3].textContent;
            const priceText = cells[5].textContent;
            
            const [amountStr, currency] = priceText.split(' ');
            const amount = parseFloat(amountStr.replace(/,/g, ''));
            
            let displayAmount = amount;
            
            if (activeFilter === 'all' && type === '구매') {
                displayAmount = -amount;
            } else if (activeFilter === 'purchase') {
                displayAmount = -amount;
            }

            if (currency === 'KRW') {
                totalKRW += displayAmount;
            } else if (currency === 'USD') {
                totalUSD += displayAmount;
            }
            
            detailsHtml += `<tr><td>${itemName}</td><td>${route}</td><td>${displayAmount.toLocaleString()} ${currency}</td></tr>`;
        });

        detailsHtml += '</tbody></table>';

        const totalColorKRW = totalKRW >= 0 ? 'color: red;' : 'color: blue;';
        const totalColorUSD = totalUSD >= 0 ? 'color: red;' : 'color: blue;';
        
        let summaryHtml = '<hr style="margin: 20px 0;"><h4 style="text-align: right;">합계</h4>';
        if (totalKRW !== 0) {
            summaryHtml += `<p style="text-align: right; font-size: 1.2em; font-weight: bold; ${totalColorKRW}">${totalKRW.toLocaleString()} KRW</p>`;
        }
        if (totalUSD !== 0) {
            summaryHtml += `<p style="text-align: right; font-size: 1.2em; font-weight: bold; ${totalColorUSD}">${totalUSD.toLocaleString()} USD</p>`;
        }
        
        summaryModalTitle.textContent = `거래 합계 (${startDateInput.value} ~ ${endDateInput.value})`;
        summaryModalBody.innerHTML = detailsHtml + summaryHtml;
        summaryModal.style.display = 'flex';
    }

    // --- 이벤트 리스너 설정 ---
    filterGroup.addEventListener('click', (e) => {
        if (e.target.tagName !== 'BUTTON' || e.target.classList.contains('is-active')) return;
        filterGroup.querySelector('.is-active').classList.remove('is-active');
        e.target.classList.add('is-active');
        fetchAndRenderTransactions();
    });

    searchForm.addEventListener('submit', (e) => {
        e.preventDefault();
        fetchAndRenderTransactions();
    });
    
    startDateInput.addEventListener('change', () => {
        updateCalcButtonState();
        fetchAndRenderTransactions();
    });
    endDateInput.addEventListener('change', () => {
        updateCalcButtonState();
        fetchAndRenderTransactions();
    });

    // [✅ 추가] 계산 버튼 클릭 및 모달 닫기 이벤트
    calcButton.addEventListener('click', showSummaryModal);
    summaryModal.querySelector('.btn-close').addEventListener('click', () => summaryModal.style.display = 'none');
    summaryModal.querySelector('.btn-cancel').addEventListener('click', () => summaryModal.style.display = 'none');

    // --- 초기 실행 ---
    updateCalcButtonState();
    fetchAndRenderTransactions();
});