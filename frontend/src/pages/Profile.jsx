import { useEffect, useState } from "react";
import toast from "react-hot-toast";
import client from "../api/client";

export default function Profile() {
  const [profile, setProfile] = useState(null);
  const [displayName, setDisplayName] = useState("");
  const [profileError, setProfileError] = useState("");
  const [savingProfile, setSavingProfile] = useState(false);

  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [passwordError, setPasswordError] = useState("");
  const [savingPassword, setSavingPassword] = useState(false);

  const [members, setMembers] = useState([]);
  const [inviteEmail, setInviteEmail] = useState("");
  const [inviteError, setInviteError] = useState("");
  const [inviting, setInviting] = useState(false);

  function loadMembers() {
    client.get("/auth/family/members").then((res) => setMembers(res.data.data));
  }

  useEffect(() => {
    client.get("/auth/me").then((res) => {
      setProfile(res.data.data);
      setDisplayName(res.data.data.displayName);
    });
    loadMembers();
  }, []);

  async function handleInviteSubmit(e) {
    e.preventDefault();
    setInviteError("");
    setInviting(true);
    try {
      const res = await client.post("/auth/invite", { email: inviteEmail });
      toast.success(res.data.data?.message || `Đã gửi lời mời đến ${inviteEmail}`);
      setInviteEmail("");
    } catch (err) {
      setInviteError(err.response?.data?.message || "Gửi lời mời thất bại");
    } finally {
      setInviting(false);
    }
  }

  async function handleProfileSubmit(e) {
    e.preventDefault();
    setProfileError("");
    setSavingProfile(true);
    try {
      const res = await client.put("/auth/me", { displayName });
      setProfile(res.data.data);
      toast.success("Cập nhật hồ sơ thành công");
    } catch (err) {
      setProfileError(err.response?.data?.message || "Cập nhật hồ sơ thất bại");
    } finally {
      setSavingProfile(false);
    }
  }

  async function handlePasswordSubmit(e) {
    e.preventDefault();
    setPasswordError("");
    setSavingPassword(true);
    try {
      await client.put("/auth/me/password", { currentPassword, newPassword });
      setCurrentPassword("");
      setNewPassword("");
      toast.success("Đổi mật khẩu thành công");
    } catch (err) {
      setPasswordError(err.response?.data?.message || "Đổi mật khẩu thất bại");
    } finally {
      setSavingPassword(false);
    }
  }

  if (!profile) return null;

  const isLocalAccount = profile.provider === "LOCAL";

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>Hồ sơ</h1>
          <p className="page-header-subtitle">Quản lý thông tin tài khoản của bạn</p>
        </div>
      </div>

      <div className="section-card">
        <h2>Thông tin cá nhân</h2>
        <form className="inline-form" onSubmit={handleProfileSubmit}>
          <label className="field">
            Email
            <input value={profile.email} disabled />
          </label>
          <label className="field">
            Tên hiển thị
            <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} required />
          </label>
          <label className="field">
            Vai trò
            <input value={profile.role} disabled />
          </label>
          <button type="submit" disabled={savingProfile}>
            {savingProfile ? "Đang lưu..." : "Lưu thay đổi"}
          </button>
        </form>
        {profileError && <p className="error-text">{profileError}</p>}
      </div>

      <div className="section-card">
        <h2>Đổi mật khẩu</h2>
        {!isLocalAccount ? (
          <p className="empty-state">
            Tài khoản này đăng nhập qua {profile.provider}, không có mật khẩu để đổi.
          </p>
        ) : (
          <form className="inline-form" onSubmit={handlePasswordSubmit}>
            <label className="field">
              Mật khẩu hiện tại
              <input
                type="password"
                value={currentPassword}
                onChange={(e) => setCurrentPassword(e.target.value)}
                required
              />
            </label>
            <label className="field">
              Mật khẩu mới
              <input
                type="password"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                minLength={8}
                required
              />
            </label>
            <button type="submit" disabled={savingPassword}>
              {savingPassword ? "Đang lưu..." : "Đổi mật khẩu"}
            </button>
          </form>
        )}
        {passwordError && <p className="error-text">{passwordError}</p>}
      </div>

      <div className="section-card">
        <h2>Thành viên gia đình</h2>
        {members.length === 0 ? (
          <p className="empty-state">Chưa có thành viên nào.</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>Tên hiển thị</th>
                <th>Email</th>
                <th>Vai trò</th>
              </tr>
            </thead>
            <tbody>
              {members.map((m) => (
                <tr key={m.id}>
                  <td>{m.displayName}</td>
                  <td>{m.email}</td>
                  <td>{m.role}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}

        {profile.role === "OWNER" && (
          <>
            <h3>Mời thành viên mới</h3>
            <form className="inline-form" onSubmit={handleInviteSubmit}>
              <label className="field">
                Email
                <input
                  type="email"
                  value={inviteEmail}
                  onChange={(e) => setInviteEmail(e.target.value)}
                  placeholder="email@example.com"
                  required
                />
              </label>
              <button type="submit" disabled={inviting}>
                {inviting ? "Đang gửi..." : "Gửi lời mời"}
              </button>
            </form>
            {inviteError && <p className="error-text">{inviteError}</p>}
          </>
        )}
      </div>
    </div>
  );
}
