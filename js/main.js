// Installer 项目页面交互脚本

// 设置当前日期
const now = new Date();
const options = { year: 'numeric', month: 'long', day: 'numeric' };
const currentDateElement = document.getElementById('currentDate');
if (currentDateElement) {
    currentDateElement.textContent = now.toLocaleDateString('zh-CN', options);
}

// 返回顶部功能
const backToTopButton = document.getElementById('backToTop');

window.addEventListener('scroll', () => {
    if (window.pageYOffset > 300) {
        backToTopButton.classList.add('show');
    } else {
        backToTopButton.classList.remove('show');
    }
});

backToTopButton.addEventListener('click', () => {
    window.scrollTo({
        top: 0,
        behavior: 'smooth'
    });
});
