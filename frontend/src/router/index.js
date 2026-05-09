import { createRouter, createWebHistory } from "vue-router";
import { useAuthStore } from "../stores/auth";
import LoginView from "../views/LoginView.vue";
import RegisterView from "../views/RegisterView.vue";
import MainLayout from "../layouts/MainLayout.vue";
import DetectTaskView from "../views/DetectTaskView.vue";
import HistoryView from "../views/HistoryView.vue";
import ProfileView from "../views/ProfileView.vue";
import UserManageView from "../views/UserManageView.vue";
import DisasterFeedView from "../views/DisasterFeedView.vue";
import RoleManageView from "../views/RoleManageView.vue";
import ChatAssistantView from "../views/ChatAssistantView.vue";

const routes = [
  { path: "/login", component: LoginView, meta: { public: true } },
  { path: "/register", component: RegisterView, meta: { public: true } },
  {
    path: "/",
    component: MainLayout,
    redirect: "/detect-task",
    meta: { requireAuth: true },
    children: [
      {
        path: "detect-task",
        name: "detect-task",
        component: DetectTaskView,
        meta: { title: "识别任务", roles: ["MONITOR", "ADMIN", "SUPER_ADMIN"] }
      },
      {
        path: "history",
        name: "history",
        component: HistoryView,
        meta: { title: "历史记录", roles: ["MONITOR", "ADMIN", "SUPER_ADMIN"] }
      },
      {
        path: "profile",
        name: "profile",
        component: ProfileView,
        meta: { title: "个人中心", roles: ["MONITOR", "ADMIN", "SUPER_ADMIN"] }
      },
      {
        path: "users",
        name: "users",
        component: UserManageView,
        meta: { title: "用户管理", roles: ["ADMIN", "SUPER_ADMIN"] }
      },
      {
        path: "disaster-feed",
        name: "disaster-feed",
        component: DisasterFeedView,
        meta: { title: "灾情动态", roles: ["ADMIN", "SUPER_ADMIN"] }
      },
      {
        path: "roles",
        name: "roles",
        component: RoleManageView,
        meta: { title: "角色管理", roles: ["SUPER_ADMIN"] }
      },
      {
        path: "ai-chat",
        name: "ai-chat",
        component: ChatAssistantView,
        meta: { title: "滑坡智能助手", roles: ["MONITOR", "ADMIN", "SUPER_ADMIN"] }
      }
    ]
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

router.beforeEach((to, from, next) => {
  const auth = useAuthStore();
  auth.refreshFromStorage();

  if (to.meta.public) {
    return next();
  }
  if (to.meta.requireAuth && !auth.token) {
    return next("/login");
  }
  if (to.meta.roles && !to.meta.roles.includes(auth.role)) {
    return next("/detect-task");
  }
  next();
});

export default router;