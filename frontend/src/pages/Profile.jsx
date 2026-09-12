import { useEffect, useState } from "react";
import toast from "react-hot-toast";
import client from "../api/client";
import { EyeIcon, EyeOffIcon } from "../components/AuthIcons";
import { TrashIcon } from "../components/AppIcons";

const RELATIONSHIP_OPTIONS = ["Bố", "Mẹ", "Ông", "Bà", "Anh", "Chị", "Em", "Con", "Cháu", "Khác"];

export default function Profile() {
  const [profile, setProfile] = useState(null);
  const [displayName, setDisplayName] = useState("");
  const [relationship, setRelationship] = useState("");
  const [profileError, setProfileError] = useState("");
  const [savingProfile, setSavingProfile] = useState(false);

  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [showCurrentPassword, setShowCurrentPassword] = useState(false);
  const [showNewPassword, setShowNewPassword] = useState(false);
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
      setRelationship(res.data.data.relationship ?? "");
    });
    loadMembers();
  }, []);

  async function handleRemoveMember(member) {
    if (!window.confirm(`Xoá ${member.displayName} khỏi gia đình? Hành động này không thể hoàn tác.`)) return;
    try {
      await client.delete(`/auth/family/members/${member.id}`);
      toast.success("Đã xoá thành viên khỏi gia đình");
      loadMembers();
    } catch (err) {
      toast.error(err.response?.data?.message || "Xoá thành viên thất bại");
    }
  }

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
      const res = await client.put("/auth/me", { displayName, relationship: relationship || null });
      setProfile(res.data.data);
      loadMembers();
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
            Quan hệ trong gia đình
            <select value={relationship} onChange={(e) => setRelationship(e.target.value)}>
              <option value="">Không chọn</option>
              {RELATIONSHIP_OPTIONS.map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </select>
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
              <div className="password-field-wrapper">
                <input
                  type={showCurrentPassword ? "text" : "password"}
                  value={currentPassword}
                  onChange={(e) => setCurrentPassword(e.target.value)}
                  required
                />
                <button
                  type="button"
                  className="password-toggle-btn"
                  onClick={() => setShowCurrentPassword((v) => !v)}
                  aria-label={showCurrentPassword ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
                >
                  {showCurrentPassword ? <EyeOffIcon /> : <EyeIcon />}
                </button>
              </div>
            </label>
            <label className="field">
              Mật khẩu mới
              <div className="password-field-wrapper">
                <input
                  type={showNewPassword ? "text" : "password"}
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  minLength={8}
                  required
                />
                <button
                  type="button"
                  className="password-toggle-btn"
                  onClick={() => setShowNewPassword((v) => !v)}
                  aria-label={showNewPassword ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
                >
                  {showNewPassword ? <EyeOffIcon /> : <EyeIcon />}
                </button>
              </div>
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
                <th>Quan hệ</th>
                {profile.role === "OWNER" && <th></th>}
              </tr>
            </thead>
            <tbody>
              {members.map((m) => (
                <tr key={m.id}>
                  <td>{m.displayName}</td>
                  <td>{m.email}</td>
                  <td>{m.role}</td>
                  <td>{m.relationship || "-"}</td>
                  {profile.role === "OWNER" && (
                    <td className="row-actions">
                      {m.role !== "OWNER" && (
                        <button
                          type="button"
                          className="icon-btn icon-btn-danger"
                          onClick={() => handleRemoveMember(m)}
                          aria-label="Xoá"
                        >
                          <TrashIcon />
                        </button>
                      )}
                    </td>
                  )}
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
