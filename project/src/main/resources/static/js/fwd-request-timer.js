// [✅ fwd-request-timer.js 파일 전체를 이 코드로 교체해주세요]

document.addEventListener('DOMContentLoaded', () => {
    const timerButtons = document.querySelectorAll('.btn-timer');

    const updateAllTimers = () => {
        const now = new Date();
        timerButtons.forEach(button => {
            const deadlineString = button.dataset.deadlineDatetime;
            if (!deadlineString) return;

            const deadline = new Date(deadlineString);
            const diff = deadline - now; // 남은 시간을 밀리초로 계산

            const card = button.closest('.request-card');
            const quoteBtn = card ? card.querySelector('.btn-quote') : null;

            if (diff <= 0) {
                // 마감 시간이 지났을 경우
                button.textContent = '마감';
                button.classList.add('is-expired');
                if (quoteBtn) {
                    quoteBtn.disabled = true;
                }
            } else {
                // [✅ 핵심 수정] 남은 시간에 따라 표시 단위를 변경하는 로직
                const totalMinutes = Math.floor(diff / (1000 * 60));
                const totalHours = Math.floor(diff / (1000 * 60 * 60));
                const days = Math.floor(diff / (1000 * 60 * 60 * 24));

                if (totalHours >= 24) {
                    // 24시간 이상 남았을 때: "Nday"
                    button.textContent = `${days} days`;
                } else if (totalHours >= 1) {
                    // 1시간 이상 24시간 미만 남았을 때: "Nhours"
                    button.textContent = `${totalHours} hours`;
                } else {
                    // 1시간 미만 남았을 때: "Nmin"
                    button.textContent = `${totalMinutes} mins`;
                }

                if (quoteBtn) {
                    quoteBtn.disabled = false;
                }
            }
        });
    };

    // 페이지 로드 시 즉시 타이머를 한 번 실행하고, 1초마다 업데이트합니다.
    updateAllTimers();
    setInterval(updateAllTimers, 1000);
});