package com.bradandmarsha.acruet.ui;

/**
 * Public landing marketing sections adapted from README (Phase 9 item 3).
 */
public final class MarketingContent {

    private MarketingContent() {
    }

    public static String html() {
        return """
                <article class="marketing">
                  <h2>About a-cruet</h2>
                  <p><strong>a-cruet</strong> is a personal web app for <strong>envelope budgeting</strong> — setting aside money for specific purposes (Christmas fund, vacation, health expenses, and similar goals) and tracking how those balances change over time.</p>
                  <p>A <strong>cruet</strong> is a small glass vessel for valued liquids like olive oil. The name also echoes <strong>&ldquo;accrue it&rdquo;</strong> — the accounting idea of letting savings build up deliberately rather than spending as it arrives.</p>

                  <h2>What it does</h2>
                  <p>Envelope budgeting treats each savings goal as its own <strong>envelope</strong>. When money comes in, you decide how much belongs in each envelope. When you spend or move money, you take it from the right envelope so you always know what each goal still has.</p>
                  <p>With a-cruet you can:</p>
                  <ul>
                    <li><strong>Apply for access</strong> — submit a short application; an administrator reviews and approves new users</li>
                    <li><strong>Sign in</strong> securely and manage your own envelopes</li>
                    <li><strong>Record deposits</strong>, <strong>withdrawals</strong>, and <strong>transfers</strong> between envelopes</li>
                    <li><strong>View reports</strong> — a spreadsheet-style history and a chart of balances over time</li>
                    <li><strong>Protect your data</strong> with a personal encryption passphrase that only you know</li>
                  </ul>
                  <p>The app is built for people who want a simple, intentional way to <strong>allocate and track savings by purpose</strong>, not a full double-entry accounting system or a bank connection.</p>

                  <h2>How your privacy works</h2>
                  <p>Your financial details — envelope names, amounts, notes, and balances — are <strong>encrypted in your browser</strong> before they ever reach the server. The service stores scrambled data it cannot read.</p>
                  <p>You choose a <strong>passphrase</strong> to unlock your ledger. The app asks you to save a <strong>recovery file</strong> when you first set up encryption. If you lose both your passphrase and that file, <strong>your data cannot be recovered</strong> — not even by an administrator. That is intentional: your savings picture stays yours alone.</p>
                  <p>Administrators can see <strong>who</strong> uses the app and <strong>how much activity</strong> there is (for example, how many envelopes or transactions exist), but they <strong>never</strong> see your actual balances or descriptions.</p>

                  <h2>Getting access</h2>
                  <p>a-cruet is <strong>invite-by-approval</strong>, not open self-registration:</p>
                  <ol>
                    <li>You fill out a short application (name, contact details, and why you want access)</li>
                    <li>You confirm your email address</li>
                    <li>An administrator reviews your request</li>
                    <li>If approved, you receive sign-in instructions and create your encryption key on first login</li>
                  </ol>
                  <p>If an application is not approved, you may apply again after a waiting period. Repeated rejections can block further applications from the same email until an administrator clears it.</p>
                </article>
                """;
    }
}
