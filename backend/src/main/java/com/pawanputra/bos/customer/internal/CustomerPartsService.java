package com.pawanputra.bos.customer.internal;

import com.pawanputra.bos.customer.api.AddressType;
import com.pawanputra.bos.customer.internal.CustomerViews.Address;
import com.pawanputra.bos.customer.internal.CustomerViews.Contact;
import com.pawanputra.bos.customer.internal.CustomerViews.Note;
import com.pawanputra.bos.identity.api.UserDirectory;
import com.pawanputra.bos.platform.error.ConflictException;
import com.pawanputra.bos.platform.error.ResourceNotFoundException;
import com.pawanputra.bos.platform.security.CurrentUser;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The things that hang off a customer: contacts, addresses and notes. Every method first loads the
 * customer within the caller's organization, so parts of another organization's customer cannot be
 * reached by guessing ids.
 */
@Service
@Transactional(readOnly = true)
public class CustomerPartsService {

    private final CustomerService customerService;
    private final CustomerContactRepository contacts;
    private final CustomerAddressRepository addresses;
    private final CustomerNoteRepository notes;
    private final UserDirectory users;

    public CustomerPartsService(
            CustomerService customerService,
            CustomerContactRepository contacts,
            CustomerAddressRepository addresses,
            CustomerNoteRepository notes,
            UserDirectory users) {
        this.customerService = customerService;
        this.contacts = contacts;
        this.addresses = addresses;
        this.notes = notes;
        this.users = users;
    }

    public record ContactDetails(String name, String designation, String email, String phone, boolean primaryContact) {
    }

    public record AddressDetails(
            AddressType type, String label, String line1, String line2, String city, String state,
            String postalCode, String countryCode, boolean defaultAddress) {
    }

    // ------------------------------------------------------------------ contacts

    public List<Contact> contacts(UUID organizationId, UUID customerId) {
        customerService.load(organizationId, customerId);
        return contacts.findAllByCustomerIdOrderByPrimaryContactDescNameAsc(customerId).stream()
                .map(Contact::from).toList();
    }

    @Transactional
    public Contact addContact(UUID organizationId, UUID customerId, ContactDetails details) {
        Customer customer = customerService.load(organizationId, customerId);
        CustomerContact contact = new CustomerContact(customer, details.name());
        // The first contact is the primary one: there must be somebody to call.
        boolean makePrimary = details.primaryContact() || contacts.countByCustomerId(customerId) == 0;
        apply(contact, details, makePrimary, customerId);
        return Contact.from(contacts.saveAndFlush(contact));
    }

    @Transactional
    public Contact updateContact(
            UUID organizationId, UUID customerId, UUID contactId, ContactDetails details, long expectedVersion) {
        customerService.load(organizationId, customerId);
        CustomerContact contact = contacts.findByIdAndCustomerId(contactId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Contact", contactId));
        requireVersion(contact.getVersion(), expectedVersion, "contact");
        apply(contact, details, details.primaryContact(), customerId);
        return Contact.from(contacts.saveAndFlush(contact));
    }

    @Transactional
    public void removeContact(UUID organizationId, UUID customerId, UUID contactId) {
        customerService.load(organizationId, customerId);
        contacts.delete(contacts.findByIdAndCustomerId(contactId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Contact", contactId)));
    }

    private void apply(CustomerContact contact, ContactDetails details, boolean primary, UUID customerId) {
        if (primary && !contact.isPrimaryContact()) {
            // One primary contact per customer: the title moves.
            contacts.findByCustomerIdAndPrimaryContactTrue(customerId).ifPresent(current -> {
                current.setPrimaryContact(false);
                contacts.saveAndFlush(current);
            });
        }
        contact.setPrimaryContact(primary);
        contact.setName(details.name());
        contact.setDesignation(details.designation());
        contact.setEmail(details.email());
        contact.setPhone(details.phone());
    }

    // ------------------------------------------------------------------ addresses

    public List<Address> addresses(UUID organizationId, UUID customerId) {
        customerService.load(organizationId, customerId);
        return addresses.findAllByCustomerIdOrderByTypeAscDefaultAddressDescCityAsc(customerId).stream()
                .map(Address::from).toList();
    }

    @Transactional
    public Address addAddress(UUID organizationId, UUID customerId, AddressDetails details) {
        Customer customer = customerService.load(organizationId, customerId);
        CustomerAddress address = new CustomerAddress(customer, details.type());
        // The first address of a type is that type's default.
        boolean makeDefault = details.defaultAddress()
                || addresses.countByCustomerIdAndType(customerId, details.type()) == 0;
        apply(address, details, makeDefault, customerId);
        return Address.from(addresses.saveAndFlush(address));
    }

    @Transactional
    public Address updateAddress(
            UUID organizationId, UUID customerId, UUID addressId, AddressDetails details, long expectedVersion) {
        customerService.load(organizationId, customerId);
        CustomerAddress address = addresses.findByIdAndCustomerId(addressId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Address", addressId));
        requireVersion(address.getVersion(), expectedVersion, "address");
        apply(address, details, details.defaultAddress(), customerId);
        return Address.from(addresses.saveAndFlush(address));
    }

    @Transactional
    public void removeAddress(UUID organizationId, UUID customerId, UUID addressId) {
        customerService.load(organizationId, customerId);
        addresses.delete(addresses.findByIdAndCustomerId(addressId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Address", addressId)));
    }

    private void apply(CustomerAddress address, AddressDetails details, boolean makeDefault, UUID customerId) {
        boolean becomesDefault = makeDefault && (!address.isDefaultAddress() || address.getType() != details.type());
        if (becomesDefault) {
            // One default per type: the billing default and the service default are independent.
            addresses.findByCustomerIdAndTypeAndDefaultAddressTrue(customerId, details.type())
                    .filter(current -> !current.equals(address))
                    .ifPresent(current -> {
                        current.setDefaultAddress(false);
                        addresses.saveAndFlush(current);
                    });
        }
        address.setType(details.type());
        address.setDefaultAddress(makeDefault);
        address.setLabel(details.label());
        address.setLine1(details.line1());
        address.setLine2(details.line2());
        address.setCity(details.city());
        address.setState(details.state());
        address.setPostalCode(details.postalCode());
        address.setCountryCode(details.countryCode());
    }

    // ------------------------------------------------------------------ notes

    public Page<Note> notes(UUID organizationId, UUID customerId, Pageable pageable) {
        customerService.load(organizationId, customerId);
        return notes.findAllByCustomerIdOrderByCreatedAtDescIdDesc(customerId, pageable).map(Note::from);
    }

    @Transactional
    public Note addNote(CurrentUser author, UUID customerId, String body) {
        Customer customer = customerService.load(author.organizationId(), customerId);
        String authorName = users.find(author.organizationId(), author.id())
                .map(UserDirectory.UserSummary::fullName).orElse(author.email());
        return Note.from(notes.saveAndFlush(new CustomerNote(customer, body, authorName)));
    }

    @Transactional
    public void removeNote(UUID organizationId, UUID customerId, UUID noteId) {
        customerService.load(organizationId, customerId);
        notes.delete(notes.findByIdAndCustomerId(noteId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Note", noteId)));
    }

    private static void requireVersion(long actual, long expected, String what) {
        if (actual != expected) {
            throw new ConflictException(
                    "This " + what + " was changed by someone else. Reload it and apply your changes again.");
        }
    }
}
