import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { authApi } from '../api/auth';

export default function RegisterPage() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPwd, setConfirmPwd] = useState('');
  const [nickname, setNickname] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (password !== confirmPwd) {
      setError('两次密码不一致');
      return;
    }
    if (username.length < 2 || username.length > 20) {
      setError('用户名长度为2-20个字符');
      return;
    }
    if (password.length < 6) {
      setError('密码至少6位');
      return;
    }

    setLoading(true);
    try {
      const res = await authApi.register({ username, password, nickname: nickname || undefined });
      if (res.data.code === 200) {
        navigate('/login', { state: { registered: true } });
      } else {
        setError(res.data.message);
      }
    } catch (err: any) {
      setError(err.response?.data?.message || '注册失败，请重试');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="page login-page">
      <div className="card">
        <h1>三国杀·竞技</h1>
        <h2>注册</h2>
        {error && <div className="error-msg">{error}</div>}
        <form onSubmit={handleSubmit}>
          <div className="field">
            <label>用户名</label>
            <input
              type="text"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="2-20个字符"
              required
            />
          </div>
          <div className="field">
            <label>密码</label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="至少6位"
              required
            />
          </div>
          <div className="field">
            <label>确认密码</label>
            <input
              type="password"
              value={confirmPwd}
              onChange={(e) => setConfirmPwd(e.target.value)}
              placeholder="再次输入密码"
              required
            />
          </div>
          <div className="field">
            <label>昵称（选填）</label>
            <input
              type="text"
              value={nickname}
              onChange={(e) => setNickname(e.target.value)}
              placeholder="默认使用用户名"
            />
          </div>
          <button type="submit" className="btn btn-primary" disabled={loading}>
            {loading ? '注册中...' : '注册'}
          </button>
        </form>
        <p className="switch-link">
          已有账号？<Link to="/login">立即登录</Link>
        </p>
      </div>
    </div>
  );
}