// Installer 更新日志页面交互脚本

// 提交日志时间线展开/收起
const toggleLogButton = document.getElementById('toggleLog');
const timelineElement = document.getElementById('timeline');

if (toggleLogButton && timelineElement) {
    toggleLogButton.addEventListener('click', () => {
        const collapsed = timelineElement.style.display === 'none';
        timelineElement.style.display = collapsed ? 'block' : 'none';
        toggleLogButton.textContent = collapsed ? '收起提交日志' : '展开提交日志';
    });
}

// 点击提交哈希复制完整 hash
document.querySelectorAll('.commit-card .hash').forEach(el => {
    el.title = '点击复制提交哈希';
    el.addEventListener('click', () => {
        const hash = el.textContent.trim();
        if (navigator.clipboard) {
            navigator.clipboard.writeText(hash).then(() => {
                const original = el.textContent;
                el.textContent = '已复制';
                setTimeout(() => { el.textContent = original; }, 1000);
            });
        }
    });
});
