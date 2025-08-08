// [✅ 이 코드로 파일 전체를 교체해주세요]
document.addEventListener('DOMContentLoaded', () => {
    const notificationArea = document.getElementById('notification-area');
    if (!notificationArea) return;

    const countElement = document.getElementById('notification-count');
    const listElement = document.getElementById('notification-list');
    const dropdown = document.getElementById('notification-dropdown');
    const notiBtn = notificationArea.querySelector('.notification-btn');
    const markAllReadBtn = document.getElementById('mark-all-read-btn');

    const updateCountUI = (count) => {
        const numericCount = parseInt(count, 10);
        countElement.textContent = numericCount;
        countElement.style.display = numericCount > 0 ? 'flex' : 'none';
    };

    const addNotificationToList = (noti) => {
        const noNotiMsg = listElement.querySelector('.no-notifications');
        if (noNotiMsg) noNotiMsg.remove();

        const li = document.createElement('li');
        li.dataset.id = noti.id;
        li.dataset.url = noti.url;
        li.innerHTML = `<div class="message">${noti.message}</div><div class="timestamp">${noti.createdAt}</div>`;
        listElement.prepend(li);
    };

    const showNoNotificationMessage = () => {
        listElement.innerHTML = '<li class="no-notifications">새로운 알림이 없습니다.</li>';
    };

    const initializeNotifications = async () => {
        try {
            // 1. 서버에서 '안 읽은' 알림만 가져옵니다.
            const response = await fetch('/api/notifications');
            if (!response.ok) throw new Error('알림 목록 로딩 실패');
            const notifications = await response.json();

            // 2. UI를 업데이트합니다.
            updateCountUI(notifications.length);
            listElement.innerHTML = '';
            if (notifications.length === 0) {
                showNoNotificationMessage();
            } else {
                notifications.forEach(addNotificationToList);
            }
        } catch (error) {
            console.error(error);
            listElement.innerHTML = '<li class="no-notifications">알림을 불러올 수 없습니다.</li>';
        }

        // 3. SSE 연결을 시작합니다.
        const eventSource = new EventSource('/api/notifications/subscribe');

        eventSource.addEventListener('unreadCount', (event) => {
            updateCountUI(event.data);
        });

        eventSource.addEventListener('notification', (event) => {
            const newNoti = JSON.parse(event.data);
            addNotificationToList(newNoti);
            const currentCount = parseInt(countElement.textContent, 10) || 0;
            updateCountUI(currentCount + 1);
        });

        eventSource.onerror = (error) => {
            console.error('SSE 오류 발생:', error);
        };
    };

    // --- 이벤트 핸들러 ---
    notiBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        dropdown.style.display = dropdown.style.display === 'block' ? 'none' : 'block';
    });

    listElement.addEventListener('click', async (e) => {
        const li = e.target.closest('li[data-id]');
        if (!li) return;

        const id = li.dataset.id;
        const url = li.dataset.url;
        
        try {
            // 서버에 읽음 처리를 요청
            await fetch(`/api/notifications/${id}/read`, { method: 'POST' });
            
            // 화면에서 즉시 제거
            li.remove(); 
            
            const currentCount = parseInt(countElement.textContent, 10);
            const newCount = Math.max(0, currentCount - 1);
            updateCountUI(newCount);

            if (listElement.children.length === 0) {
                showNoNotificationMessage();
            }
        } catch (error) { 
            console.error("알림 읽음 처리 실패:", error);
        } finally {
            if (url && url !== 'null') {
                window.location.href = url;
            }
        }
    });

    markAllReadBtn.addEventListener('click', async (e) => {
        e.stopPropagation();
        try {
            await fetch('/api/notifications/read/all', { method: 'POST' });
            updateCountUI(0);
            showNoNotificationMessage();
        } catch (error) { 
            console.error("모두 읽음 처리 실패:", error); 
        }
    });

    document.addEventListener('click', (e) => {
        if (!notificationArea.contains(e.target)) {
            dropdown.style.display = 'none';
        }
    });

    initializeNotifications();
});