package org.multibit.hd.ui.views.components.enter_amount;

import com.google.common.base.Optional;
import com.google.common.eventbus.Subscribe;
import net.miginfocom.swing.MigLayout;
import org.multibit.hd.core.api.MessageKey;
import org.multibit.hd.core.events.ExchangeRateChangedEvent;
import org.multibit.hd.core.utils.Numbers;
import org.multibit.hd.ui.i18n.Languages;
import org.multibit.hd.ui.views.AbstractView;
import org.multibit.hd.ui.views.components.Labels;
import org.multibit.hd.ui.views.components.Panels;
import org.multibit.hd.ui.views.components.TextBoxes;
import org.multibit.hd.ui.views.components.text_fields.FormattedDecimalField;
import org.multibit.hd.ui.views.fonts.AwesomeDecorator;
import org.multibit.hd.ui.views.fonts.AwesomeIcon;

import javax.swing.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * <p>View to provide the following to UI:</p>
 * <ul>
 * <li>Presentation of a Bitcoin and local currency amount</li>
 * <li>Support for instant bi-directional conversion through exchange rate</li>
 * </ul>
 *
 * @since 0.0.1
 *  
 */
public class EnterAmountView extends AbstractView<EnterAmountModel> {

  // View components
  private FormattedDecimalField bitcoinAmountText;
  private FormattedDecimalField localAmountText;

  private JLabel exchangeRateStatusLabel = new JLabel("");
  private JLabel exchangeNameLabel = new JLabel("");
  private JLabel approximatelyLabel = new JLabel("");
  private JLabel localCurrencySymbolLabel = new JLabel("");

  private Optional<ExchangeRateChangedEvent> latestExchangeEvent = Optional.absent();

  /**
   * @param model The model backing this view
   */
  public EnterAmountView(EnterAmountModel model) {
    super(model);

  }

  @Override
  public JPanel newPanel() {

    panel = Panels.newPanel(new MigLayout(
      "fillx,insets 0", // Layout
      "[][][][][][]", // Columns
      "[][][][]" // Rows
    ));

    // Keep track of the amount fields
    bitcoinAmountText = TextBoxes.newBitcoinAmount(20_999_999.12345678);
    localAmountText = TextBoxes.newCurrencyAmount(20_999_999_123.45678);

    approximatelyLabel = Labels.newApproximately();
    localCurrencySymbolLabel = Labels.newLocalCurrencySymbol();
    exchangeNameLabel = Labels.newCurrentExchangeName();

    // Bind a key listener to allow instant update of UI to amount changes
    bitcoinAmountText.addKeyListener(new KeyAdapter() {
      @Override
      public void keyReleased(KeyEvent e) {
        updateLocalAmount();
      }
    });

    localAmountText.addKeyListener(new KeyAdapter() {
      @Override
      public void keyReleased(KeyEvent e) {
        updateBitcoinAmount();
      }

    });

    // Add to the panel
    panel.add(Labels.newEnterAmount(), "span 4,grow,push,wrap");
    panel.add(Labels.newBitcoinCurrencySymbol());
    panel.add(bitcoinAmountText);
    panel.add(approximatelyLabel, "pushy,baseline");
    panel.add(localCurrencySymbolLabel, "pushy,baseline");
    panel.add(localAmountText, "wrap");
    panel.add(exchangeRateStatusLabel, "span 4,push,wrap");
    panel.add(exchangeNameLabel, "span 4,push,wrap");

    setLocalAmountVisibility();

    return panel;


  }

  @Override
  public void updateModel() {
  }

  @Subscribe
  public void onExchangeRateChanged(ExchangeRateChangedEvent event) {

    this.latestExchangeEvent = Optional.fromNullable(event);

    setLocalAmountVisibility();

    // Rate has changed so trigger an update based on focus
    if (bitcoinAmountText.hasFocus()) {
      updateLocalAmount();
    } else {
      updateBitcoinAmount();
    }

  }

  /**
   * <p>Handles the process of updating the visibility of the local amount</p>
   * <p>This is required when an exchange has failed to provide an exchange rate in the current session</p>
   */
  private void setLocalAmountVisibility() {

    if (latestExchangeEvent.isPresent()) {

      setLocalCurrencyComponentVisibility(true);

      // Set the exchange rate provider
      exchangeNameLabel.setText(Labels.newCurrentExchangeName().getText());

      // Rate may be valid
      setExchangeRateStatus(latestExchangeEvent.get().isValid());

    } else {

      // Never had a rate so hide the local currency components
      setLocalCurrencyComponentVisibility(false);

      // Rate is not valid by definition
      setExchangeRateStatus(false);

    }

  }

  /**
   * @param visible True if the local currency components should be visible
   */
  private void setLocalCurrencyComponentVisibility(boolean visible) {

    // We can show local currency components
    this.approximatelyLabel.setVisible(visible);
    this.localCurrencySymbolLabel.setVisible(visible);
    this.localAmountText.setVisible(visible);
    this.exchangeNameLabel.setVisible(visible);

  }

  /**
   * @param valid True if the exchange rate is present and valid
   */
  private void setExchangeRateStatus(boolean valid) {

    if (valid) {
      // Update the label to show a check mark
      AwesomeDecorator.bindIcon(
        AwesomeIcon.CHECK,
        exchangeRateStatusLabel,
        true,
        AwesomeDecorator.NORMAL_ICON_SIZE
      );
      exchangeRateStatusLabel.setText(Languages.safeText(MessageKey.EXCHANGE_RATE_STATUS_OK));
    } else {
      // Update the label to show a cross
      AwesomeDecorator.bindIcon(
        AwesomeIcon.TIMES,
        exchangeRateStatusLabel,
        true,
        AwesomeDecorator.NORMAL_ICON_SIZE
      );
      exchangeRateStatusLabel.setText(Languages.safeText(MessageKey.EXCHANGE_RATE_STATUS_WARN));
    }

  }

  /**
   * Update the Bitcoin amount based on a change in the local amount
   */
  private void updateBitcoinAmount() {

    if (latestExchangeEvent.isPresent()) {

      String text = localAmountText.getText();
      Optional<Double> value = Numbers.parseDouble(text);

      if (value.isPresent()) {
        BigDecimal localAmount = new BigDecimal(value.get()).setScale(8, RoundingMode.HALF_EVEN);
        BigDecimal bitcoinAmount = localAmount
          .divide(latestExchangeEvent.get().getRate(), 12, RoundingMode.HALF_EVEN);
        // Use double for display
        bitcoinAmountText.setValue(bitcoinAmount.doubleValue());
      } else {
        bitcoinAmountText.setText("");
      }
    }

    setLocalAmountVisibility();
  }

  /**
   * Update the local amount based on a change in the Bitcoin amount
   */
  private void updateLocalAmount() {

    if (latestExchangeEvent.isPresent()) {
      String text = bitcoinAmountText.getText();
      Optional<Double> value = Numbers.parseDouble(text);

      if (value.isPresent()) {
        BigDecimal bitcoinAmount = new BigDecimal(value.get()).setScale(12, RoundingMode.HALF_EVEN);
        BigDecimal localAmount = bitcoinAmount
          .multiply(latestExchangeEvent.get().getRate())
          .setScale(8, RoundingMode.HALF_EVEN);
        // Use double for display
        localAmountText.setValue(localAmount.doubleValue());
      } else {
        localAmountText.setText("");
      }
    }

    setLocalAmountVisibility();
  }

}