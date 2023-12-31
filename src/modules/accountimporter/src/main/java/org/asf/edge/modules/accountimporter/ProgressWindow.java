package org.asf.edge.modules.accountimporter;

import java.awt.BorderLayout;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;
import javax.swing.text.DefaultCaret;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;
import javax.swing.JTextArea;
import javax.swing.JProgressBar;

public class ProgressWindow {
	public JFrame frm;
	public static boolean shown = false;

	public static class WindowAppender extends AbstractAppender {

		@SuppressWarnings("deprecation")
		protected WindowAppender(PatternLayout layout) {
			super("Window Appender", null, layout);
			start();
		}

		@Override
		public void append(LogEvent event) {
			if (frame == null)
				return;
			if (!event.getLevel().isInRange(Level.FATAL, Level.INFO))
				return;
			String message = new String(getLayout().toByteArray(event));
			if (message.endsWith("\n"))
				message = message.substring(0, message.length() - 1);

			if (message.contains("\n"))
				for (String ln : message.split("\n"))
					frame.log(ln);
			else
				frame.log(message);
		}

		public static ProgressWindow frame = new ProgressWindow();

		public static void showWindow() {
			shown = true;
			frame = new ProgressWindow();
			final LoggerContext context = LoggerContext.getContext(false);
			final Configuration config = context.getConfiguration();
			final PatternLayout layout = PatternLayout.createDefaultLayout();
			final Appender appender = new WindowAppender(layout);
			config.addAppender(appender);
			updateLoggers(appender, context.getConfiguration());
			frame.frm.setVisible(true);
		}

		public static void closeWindow() {
			if (!shown)
				return;
			shown = false;
			if (frame != null) {
				frame.frm.dispose();
				frame = null;
			}
			LoggerContext.getContext(false).getConfiguration().getRootLogger().removeAppender("Window Appender");
		}

		private static void updateLoggers(final Appender appender, final Configuration config) {
			final Level level = null;
			final Filter filter = null;
			for (final LoggerConfig loggerConfig : config.getLoggers().values()) {
				loggerConfig.addAppender(appender, level, filter);
			}
			config.getRootLogger().addAppender(appender, level, filter);
		}

		public static void setMax(int max) {
			if (frame == null)
				return;
			frame.progressBar.setMaximum(max);
		}

		public static void setValue(int val) {
			if (frame == null)
				return;
			frame.progressBar.setValue(val);
		}

		public static int getMax() {
			if (frame == null)
				return 0;
			return frame.progressBar.getMaximum();
		}

		public static int getValue() {
			if (frame == null)
				return 0;
			return frame.progressBar.getValue();
		}

		public static void increaseProgress() {
			if (!shown)
				return;
			if (frame.progressBar.getValue() + 1 > frame.progressBar.getMaximum())
				return;
			frame.progressBar.setValue(frame.progressBar.getValue() + 1);
		}

		public static void fatalError() {
			if (!shown)
				return;
			JOptionPane.showMessageDialog(frame.frm, "A fatal error occured:\n" + frame.lastMessageText, "Fatal Error",
					JOptionPane.ERROR_MESSAGE);
		}

		public static void fatalError(String msg) {
			if (!shown)
				return;
			JOptionPane.showMessageDialog(frame.frm, "A fatal error occured:\n" + msg, "Fatal Error",
					JOptionPane.ERROR_MESSAGE);
		}

		public static void log(String message) {
			if (frame == null)
				return;
			frame.log(message);
		}

		public static void lockLabel() {
			if (frame == null)
				return;
			frame.lockLabel = true;
		}

		public static void unlockLabel() {
			if (frame == null)
				return;
			frame.lockLabel = false;
		}

		public static void setLabel(String message) {
			if (frame == null)
				return;
			frame.lastMessage.setText(message);
			frame.lastMessageText = message;
		}

	}

	private JTextArea textArea = new JTextArea();
	private JLabel lastMessage = new JLabel("Account import in progress...");
	private String lastMessageText;
	private boolean lockLabel = false;

	public void log(String message) {
		if (!shown)
			return;

		if (!lockLabel) {
			lastMessageText = message;
			if (!message.isEmpty())
				lastMessage.setText(message);
		}

		textArea.setText(textArea.getText() + message + "\n");
		textArea.setCaretPosition(textArea.getDocument().getLength());
	}

	private JPanel contentPane;
	private final JProgressBar progressBar = new JProgressBar();

	/**
	 * Create the frame.
	 */
	public ProgressWindow() {
		if (shown) {
			frm = new JFrame();
			frm.setTitle("Import in progress...");
			frm.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			frm.setBounds(100, 100, 653, 300);
			frm.setResizable(false);
			frm.setLocationRelativeTo(null);
		}
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		contentPane.setLayout(new BorderLayout(0, 0));

		if (shown)
			frm.setContentPane(contentPane);

		textArea.setEditable(false);
		JScrollPane pane = new JScrollPane(textArea);
		pane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

		DefaultCaret caret = (DefaultCaret) textArea.getCaret();
		caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

		contentPane.add(pane, BorderLayout.CENTER);

		JPanel panel = new JPanel();
		contentPane.add(panel, BorderLayout.NORTH);

		panel.add(lastMessage);
		progressBar.setMaximum(0);
		progressBar.setValue(0);
		contentPane.add(progressBar, BorderLayout.SOUTH);
	}
}
