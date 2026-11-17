import { Link } from "react-router-dom";
import { BACKUP_RETENTION_DAYS, CONTACT_EMAIL, LAST_UPDATED, OPERATOR } from "../config/legal";

export default function PrivacyPage() {
  return (
    <main className="container legal">
      <p>
        <Link to="/">Back to the app</Link>
      </p>

      <h1>Privacy notice and medical disclaimer</h1>
      <p className="muted">Last updated: {LAST_UPDATED}</p>

      <section className="card">
        <h2>Medical disclaimer</h2>
        <p>
          VitalSafe <strong>records and charts the readings you enter</strong>. It does not diagnose, treat or give
          medical advice, and it is not a medical device. It never tells you that a reading is healthy or unhealthy.
        </p>
        <p>
          Any target range in the app is a range <strong>you</strong> chose, and the app only reports whether a reading
          is within, above or below it. Talk to a doctor or another qualified health professional about your readings.
        </p>
        <p>
          <strong>VitalSafe is not for emergencies.</strong> If you feel unwell or think something is wrong, contact
          your local emergency services.
        </p>
      </section>

      <section className="card">
        <h2>Who is responsible for your data</h2>
        <p>
          {OPERATOR} runs VitalSafe and decides how your data is used. You can contact us at {CONTACT_EMAIL}.
        </p>
      </section>

      <section className="card">
        <h2>What we store</h2>
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th>Data</th>
                <th>How it is stored</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td>Your email address</td>
                <td>Plain text, to log you in</td>
              </tr>
              <tr>
                <td>Your password</td>
                <td>Never stored. Only a one-way BCrypt hash is kept</td>
              </tr>
              <tr>
                <td>Reading values and notes</td>
                <td>Encrypted (AES-256-GCM) before they are saved</td>
              </tr>
              <tr>
                <td>Your target ranges</td>
                <td>Encrypted in the same way</td>
              </tr>
              <tr>
                <td>The kind of reading, its unit and when it was taken</td>
                <td>Plain text, because the database must sort and filter them</td>
              </tr>
              <tr>
                <td>Your consent record</td>
                <td>The date and time you accepted this notice, and which version you accepted</td>
              </tr>
              <tr>
                <td>Login sessions</td>
                <td>A hash of your session token and its expiry. The token itself is not stored</td>
              </tr>
              <tr>
                <td>Technical data</td>
                <td>
                  Your IP address is held briefly in memory to limit repeated login attempts. Server software may also
                  record addresses in its logs
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <p>We do not ask for your name, date of birth or location, and we use no advertising or tracking tools.</p>
      </section>

      <section className="card">
        <h2>How it is protected, and the limits</h2>
        <ul>
          <li>Passwords are hashed, and sessions use short-lived tokens that are replaced when used.</li>
          <li>Values and notes are encrypted in the database, so a stolen database or backup is not readable without the key.</li>
          <li>You can only see your own readings, and repeated login attempts are rate limited.</li>
        </ul>
        <p>
          <strong>This is not end-to-end encryption.</strong> The encryption key is held by the operator of the service, so
          the operator is technically able to read your readings. No system is perfectly secure, so please use a
          strong, unique password.
        </p>
      </section>

      <section className="card">
        <h2>Cookies</h2>
        <p>
          VitalSafe sets one cookie, <code>refresh_token</code>. It keeps you signed in for up to 7 days, page scripts
          cannot read it, and it is only sent to the sign-in endpoints. There are no advertising or tracking cookies.
        </p>
      </section>

      <section className="card">
        <h2>Who else handles your data</h2>
        <p>
          We do not share your data with other companies. VitalSafe currently runs on the operator&apos;s own computer.
          If that changes, for example by moving to a hosting provider, this notice will be updated to name it.
        </p>
      </section>

      <section className="card">
        <h2>Your choices, and what is not available yet</h2>
        <ul>
          <li>
            <strong>You can</strong> add, change and delete individual readings, and download all of them as a CSV file or as
            standard FHIR data.
          </li>
          <li>
            <strong>You can delete your whole account in the app</strong> (open Account from your email address at the top). It removes your login, your readings and your target ranges from the database straight away.
            Backups made before you delete your account may still contain your data until they are deleted, which happens after {BACKUP_RETENTION_DAYS} days. You can also ask us at {CONTACT_EMAIL}.
          </li>
        </ul>
        <p>
          Depending on where you live, you may have other rights, such as asking us to correct your data or to restrict how it
          is used. Contact us and we will respond.
        </p>
      </section>

      <section className="card">
        <h2>Children</h2>
        <p>VitalSafe is not intended for children under 16.</p>
      </section>

      <section className="card">
        <h2>Changes to this notice</h2>
        <p>If we change how we handle your data, we will update this page and its date.</p>
      </section>
    </main>
  );
}